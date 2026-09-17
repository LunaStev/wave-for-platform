import assert from 'node:assert/strict';
import {mkdtempSync, mkdirSync, rmSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {dirname, join} from 'node:path';
import test from 'node:test';
import {checkRepository} from './check.mjs';

function fixture(t, overrides = {}) {
  const root = mkdtempSync(join(tmpdir(), 'wave-check-'));
  t.after(() => rmSync(root, {recursive: true, force: true}));
  const files = {
    '.idea/modules.xml': '<project><component><modules><module filepath="$PROJECT_DIR$/app/app.iml"/></modules></component></project>',
    'app/app.iml': '<module><component><content><sourceFolder url="file://$MODULE_DIR$/src" isTestSource="false"/></content></component></module>',
    'app/src/Main.kt': 'package example\nclass Main',
    ...overrides,
  };
  for (const [file, content] of Object.entries(files)) {
    if (content === null) continue;
    mkdirSync(dirname(join(root, file)), {recursive: true});
    writeFileSync(join(root, file), content);
  }
  return checkRepository(root, Object.keys(files));
}

test('accepts a valid project without a generated index', t => {
  const result = fixture(t);
  assert.deepEqual(result.issues, []);
  assert.equal(result.generatedIndexPresent, false);
  assert.equal(result.checkedFiles, 1);
});

test('reports missing module files, dependencies, and source roots', t => {
  const result = fixture(t, {
    '.idea/modules.xml': '<modules><module filepath="$PROJECT_DIR$/app/app.iml"/><module filepath="$PROJECT_DIR$/gone/gone.iml"/></modules>',
    'app/app.iml': '<module><sourceFolder url="file://$MODULE_DIR$/missing"/><orderEntry type="module" module-name="removed"/></module>',
  });
  assert.equal(result.issues.length, 3);
  assert.match(result.issues.map(issue => issue.message).join('\n'), /Missing module file/);
  assert.match(result.issues.map(issue => issue.message).join('\n'), /Unknown module dependency/);
  assert.match(result.issues.map(issue => issue.message).join('\n'), /Missing source root/);
});

test('reports both stale and missing generated modules', t => {
  const result = fixture(t, {'build/bazel-targets.json': '{"modules":{"removed":{}}}'});
  assert.equal(result.issues.length, 2);
  assert.ok(result.generatedIndexPresent);
});

test('catches removed imports and XML registrations', t => {
  const result = fixture(t, {
    'app/src/Main.kt': 'package example\nimport com.intellij.platform.feedback.FeedbackSurvey\nclass Main',
    'app/src/plugin.xml': '<idea-plugin><actions><action class="com.intellij.terminal.frontend.action.TerminalFeedbackAction"/></actions><content namespace="jetbrains"><module name="gone" loading="required"/></content></idea-plugin>',
  });
  assert.equal(result.issues.length, 3);
  assert.equal(result.issues.find(issue => issue.file.endsWith('.kt')).line, 2);
});

test('distinguishes required content, optional content, and synthetic test descriptors', t => {
  const result = fixture(t, {
    'app/src/plugin.xml': `<idea-plugin><content namespace="jetbrains">
      <module name="app._test"/>
      <module name="missing.embedded" loading="embedded"/>
      <module name="missing.optional" loading="optional"/>
      <module name="missing.default"/>
      <module name="missing.conditional" required-if-available="another.product"/>
    </content></idea-plugin>`,
  });
  assert.equal(result.issues.length, 1);
  assert.equal(result.issues[0].line, 3);
  assert.equal(result.warnings.length, 3);
});

test('ignores fixtures, comments, string literals, and similar package names', t => {
  const result = fixture(t, {
    'app/src/Main.kt': '/*\nimport com.intellij.platform.feedback.FeedbackSurvey\n*/\nval sample = """\nimport org.jetbrains.kotlin.jsr223.Provider\n"""\nimport com.intellij.platform.feedbackOther.Kept',
    'app/src/plugin.xml': '<idea-plugin><!-- <content namespace="jetbrains"><module name="gone"/></content> --></idea-plugin>',
    'app/src/testData/Broken.kt': 'import com.intellij.platform.feedback.FeedbackSurvey',
  });
  assert.deepEqual(result.issues, []);
});

test('finds previously unknown missing internal classes without a denylist', t => {
  const result = fixture(t, {
    'app/src/Main.kt': 'package example\n\nimport com.intellij.removed.BrandNewMissing as Missing\nimport com.intellij.removed.BrandNewMissing.Nested\nclass Main',
  });
  assert.deepEqual(result.issues, []);
  assert.equal(result.importCandidates.length, 1);
  assert.equal(result.importCandidates[0].name, 'com.intellij.removed.BrandNewMissing');
  assert.deepEqual(result.importCandidates[0].locations.map(location => location.line), [3, 4]);
});

test('indexes real package declarations, secondary Java types and Kotlin facades', t => {
  const result = fixture(t, {
    'app/src/Main.kt': `package example
import com.intellij.actual.Secondary.Nested
import com.intellij.actual.Alias
import com.intellij.actual.UtilityFns.call
import com.intellij.actual.UtilitiesKt.call
import com.intellij.actual.Service.Companion
import com.intellij.actual.Mode
import com.intellij.actual.EDT
import com.intellij.actual.Render
import com.intellij.actual.KotlinMode
import com.intellij.actual.BuilderKt.call
import java.util.ArrayList
import org.jetbrains.kotlin.LibraryClass
import com.intellij.actual.topLevelExtension
import com.intellij.actual.*`,
    'app/src/Elsewhere.java': 'package com.intellij.actual; class Secondary { class Nested {} } enum Mode { ONE }',
    'app/src/Service.kt': 'package com.intellij.actual\ntypealias Alias = String\nobject Service\nenum class KotlinMode { ONE }',
    'app/src/builder.kt': 'package com.intellij.actual\nfun call() = Unit',
    'app/src/Utilities.kt': 'package com.intellij.actual\nfun call() = Unit\nval Dispatchers.EDT: Dispatcher get() = TODO()\nfun Render() = Unit',
    'app/src/Custom.kt': '@file:JvmName("UtilityFns")\npackage com.intellij.actual\nfun call() = Unit',
  });
  assert.deepEqual(result.importCandidates, []);
});

test('test roots and fixtures cannot satisfy production imports', t => {
  const result = fixture(t, {
    'app/app.iml': '<module><sourceFolder url="file://$MODULE_DIR$/src"/><sourceFolder url="file://$MODULE_DIR$/tests" isTestSource="true"/></module>',
    'app/src/Main.kt': 'import com.intellij.example.TestOnly\nimport com.intellij.example.FixtureOnly',
    'app/tests/TestOnly.kt': 'package com.intellij.example\nclass TestOnly',
    'app/src/testData/FixtureOnly.kt': 'package com.intellij.example\nclass FixtureOnly',
  });
  assert.equal(result.importCandidates.length, 2);
});

test('an explicitly declared test root nested in production is excluded', t => {
  const result = fixture(t, {
    'app/app.iml': '<module><sourceFolder url="file://$MODULE_DIR$/src"/><sourceFolder url="file://$MODULE_DIR$/src/tests" isTestSource="true"/></module>',
    'app/src/tests/Bad.kt': 'import com.intellij.unknown.DeletedApi',
  });
  assert.deepEqual(result.importCandidates, []);
  assert.equal(result.checkedFiles, 1);
});
