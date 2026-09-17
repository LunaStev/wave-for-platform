import {execFileSync} from 'node:child_process';
import {existsSync, readFileSync} from 'node:fs';
import {basename, dirname, relative, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {ImportIndex} from './check-imports.mjs';
import {runCompilationCheck} from './check-build.mjs';

const scriptPath = fileURLToPath(import.meta.url);
const defaultRoot = resolve(dirname(scriptPath), '..');

const removedPackages = [
  'com.intellij.devkit.scaffolding',
  'org.jetbrains.kotlin.idea.jvm.k2.scratch',
  'com.intellij.cce.java.evaluation',
  'com.intellij.cce.java.project',
  'com.intellij.cce.java.test',
  'com.intellij.debugger.mockJDI',
  'com.intellij.debugger.engine.dfaassist',
  'com.intellij.psi.impl.source.jsp',
  'com.intellij.tools.ide.starter.product.idea.ultimate',
  'com.intellij.tasks.context.java',
  'org.jetbrains.idea.devkit.dcevm',
  'com.intellij.java.dev.psiViewer.debug',
  'com.intellij.platform.feedback',
  'org.jetbrains.kotlin.jsr223',
  'org.jetbrains.kotlin.idea.compilerPlugin.scripting.gradleJava',
  'org.jetbrains.kotlin.idea.search.refIndex.bta',
  'org.jetbrains.plugins.terminal.block.feedback',
  'org.jetbrains.plugins.terminal.block.completion.feedback',
];
const removedClasses = [
  'org.jetbrains.kotlin.idea.core.script.k2.mainkts.codeInsight.MainKtsDependsOnCompletionProvider',
  'org.jetbrains.kotlin.idea.core.script.k2.mainkts.codeInsight.MainKtsDependsOnCompletionContributor',
  'org.jetbrains.kotlin.idea.core.script.k2.mainkts.codeInsight.MainKtsDependsOnCompletionConfidence',
  'org.jetbrains.kotlin.idea.k2.refactoring.inline.JavaToKotlinInlineHandler',
  'org.jetbrains.kotlin.idea.search.refIndex.IncrementalKotlinCompilerReferenceIndexStorage',
  'org.jetbrains.idea.devkit.testAssistant.TestDataGuessByTestDiscoveryUtil',
  'com.intellij.execution.testframework.JavaTestLocator',
  'org.jetbrains.idea.devkit.run.DevKitApplicationPatcher',
  'org.jetbrains.idea.devkit.run.IdeStarterRunConfigurationExtension',
  'org.jetbrains.idea.devkit.run.IdeStarterRunSettings',
  'org.jetbrains.idea.devkit.run.ToggleInstallerAction',
  'org.jetbrains.idea.devkit.run.ToggleSplitModeAction',
  'org.jetbrains.idea.devkit.run.JUnitDevKitPatcher',
  'org.jetbrains.idea.devkit.run.JUnitDevKitUnitTestingSettings',
  'org.jetbrains.idea.devkit.run.PluginConfigurationType',
  'org.jetbrains.idea.devkit.run.PluginRunConfiguration',
  'org.jetbrains.idea.devkit.run.PluginRunConfigurationEditor',
  'org.jetbrains.idea.devkit.requestHandlers.BuiltInServerConnectionData',
  'org.jetbrains.idea.devkit.requestHandlers.passDataAboutBuiltInServer',
  'com.intellij.debugger.DebuggerTestCase',
  'com.intellij.debugger.ExecutionWithDebuggerToolsTestCase',
  'com.intellij.debugger.DebuggerBreakpointTestUtils',
  'com.intellij.debugger.BreakpointComment',
  'com.intellij.debugger.MockConfiguration',
  'com.intellij.debugger.engine.MockDebugProcess',
  'com.intellij.debugger.impl.DescriptorTestCase',
  'com.intellij.debugger.impl.SynchronizationBasedSemaphore',
  'com.intellij.debugger.impl.DebuggerUtilsImpl',
  'com.intellij.debugger.ui.JVMDebuggerEvaluatorTest',
  'com.intellij.psi.JspPsiUtil',
  'com.intellij.jsp.JspSpiUtil',
  'com.intellij.psi.jsp.JspFile',
  'com.intellij.psi.jsp.JspLanguage',
  'com.intellij.psi.jsp.JspxLanguage',
  'com.intellij.psi.jsp.JavaJspRecursiveElementVisitor',
  'com.intellij.execution.configurations.JavaCommandLineStateUtil',
  'com.intellij.execution.configurations.JavaParameters',
  'com.intellij.execution.filters.ArithmeticExceptionInfo',
  'com.intellij.execution.filters.ArrayCopyIndexOutOfBoundsExceptionInfo',
  'com.intellij.execution.filters.ArrayIndexOutOfBoundsExceptionInfo',
  'com.intellij.execution.filters.AssertionErrorInfo',
  'com.intellij.execution.filters.ClassCastExceptionInfo',
  'com.intellij.execution.filters.ExceptionAnalysisProvider',
  'com.intellij.execution.filters.ExceptionInfo',
  'com.intellij.execution.filters.ExceptionWorker',
  'com.intellij.execution.filters.JetBrainsNotNullInstrumentationExceptionInfo',
  'com.intellij.execution.filters.NegativeArraySizeExceptionInfo',
  'com.intellij.execution.filters.NullPointerExceptionInfo',
  'com.intellij.slicer.DataflowExceptionAnalysisProvider',
  'com.intellij.execution.JavaModuleNameStacktraceModifier',
  'com.intellij.ide.macro.JavaDocPathMacro',
  'com.intellij.javadoc.JavadocConfigurable',
  'com.intellij.javadoc.JavadocConfiguration',
  'com.intellij.javadoc.JavadocGenerationAdditionalUi',
  'com.intellij.javadoc.JavadocGenerationManager',
  'com.intellij.javadoc.JavadocGeneratorRunProfile',
  'com.intellij.javadoc.actions.GenerateJavadocAction',
  'com.intellij.psi.impl.source.resolve.reference.impl.providers.MethodPropertyReference',
  'com.intellij.execution.environment.JvmEnvironmentKeyProvider',
  'com.intellij.ide.warmup.JdkWarmupProjectActivity',
  'com.intellij.ide.warmup.JdkWarmupConfigurator',
  'org.jetbrains.idea.devkit.requestHandlers.HttpDebugListener',
  'com.intellij.debugger.impl.GenericDebuggerRunner',
  'com.intellij.debugger.impl.attach.JavaAttachDebuggerProvider',
  'com.intellij.terminal.frontend.action.TerminalFeedbackAction',
  'com.intellij.execution.filters.ExceptionFilterFactory',
  'com.intellij.execution.filters.ExceptionInfoCache',
  'com.intellij.execution.filters.ExceptionLineParser',
  'com.intellij.execution.filters.ExceptionLineParserFactory',
  'com.intellij.openapi.vcs.contentAnnotation.VcsContentAnnotationExceptionFilter',
  'com.intellij.openapi.vcs.contentAnnotation.VcsContentAnnotationExceptionFilterFactory',
];

function withoutXmlComments(text) {
  return text.replace(/<!--[\s\S]*?-->/g, match => match.replace(/[^\n]/g, ' '));
}

function attributes(tag) {
  return Object.fromEntries([...tag.matchAll(/([\w:-]+)\s*=\s*(["'])(.*?)\2/gs)].map(([, key, , value]) => [
    key, value.replace(/&(amp|quot|apos|lt|gt);/g, (_, entity) => ({amp: '&', quot: '"', apos: "'", lt: '<', gt: '>'})[entity]),
  ]));
}

function tags(text, name) {
  return [...withoutXmlComments(text).matchAll(new RegExp(`<${name}\\b[^>]*>`, 'g'))];
}

function isRemoved(name) {
  return removedClasses.includes(name) || removedPackages.some(prefix => name === prefix || name.startsWith(`${prefix}.`));
}

function sourceCode(text) {
  // Ignore comments and string literals, but preserve line numbers.
  return text.replace(/"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\/\*[\s\S]*?\*\/|\/\/[^\n]*/g,
    match => match.replace(/[^\n]/g, ' '));
}

export function checkRepository(root, fileNames, {imports = true} = {}) {
  const issues = [];
  const warnings = [];
  const report = (file, line, message) => issues.push({file, line, message});
  const read = file => readFileSync(resolve(root, file), 'utf8');
  const modules = new Map();
  const sourceRoots = new Set();
  const testRoots = new Set();
  const registry = '.idea/modules.xml';
  const registryText = read(registry);
  for (const [tag] of tags(registryText, 'module')) {
    const path = attributes(tag).filepath;
    if (!path?.startsWith('$PROJECT_DIR$/') || !path.endsWith('.iml')) {
      report(registry, 1, `Invalid module path: ${path ?? '(missing)'}`);
      continue;
    }
    const file = path.slice('$PROJECT_DIR$/'.length);
    const name = basename(file, '.iml');
    if (modules.has(name)) report(registry, 1, `Duplicate module: ${name}`);
    modules.set(name, file);
  }
  if (modules.size === 0) report(registry, 1, 'No modules are registered.');

  for (const [name, file] of modules) {
    if (!existsSync(resolve(root, file))) {
      report(registry, 1, `Missing module file: ${file}`);
      continue;
    }
    const text = read(file);
    for (const [tag] of tags(text, 'orderEntry')) {
      const attr = attributes(tag);
      if (attr.type === 'module' && !modules.has(attr['module-name'])) {
        report(file, 1, `Unknown module dependency: ${attr['module-name']}`);
      }
    }
    for (const [tag] of tags(text, 'sourceFolder')) {
      const attr = attributes(tag);
      const path = attr.url?.replace('file://', '')
        .replaceAll('$MODULE_DIR$', resolve(root, dirname(file))).replaceAll('$PROJECT_DIR$', root);
      if (!path || path.includes('$')) {
        report(file, 1, `Cannot resolve source root: ${attr.url}`);
        continue;
      }
      if (!existsSync(path)) report(file, 1, `Missing source root: ${relative(root, path)}`);
      if (attr.isTestSource === 'true' || attr.type?.includes('test')) testRoots.add(resolve(path));
      else sourceRoots.add(resolve(path));
    }
  }

  const indexPath = 'build/bazel-targets.json';
  const generatedIndexPresent = existsSync(resolve(root, indexPath));
  if (generatedIndexPresent) {
    const generated = JSON.parse(read(indexPath)).modules;
    if (!generated || typeof generated !== 'object') throw new Error(`Invalid module index: ${indexPath}`);
    for (const name of Object.keys(generated)) {
      if (!modules.has(name)) report(indexPath, 1, `Generated module was removed: ${name}. Regenerate Bazel settings.`);
    }
    for (const name of modules.keys()) {
      if (!Object.hasOwn(generated, name)) report(indexPath, 1, `Generated module is missing: ${name}. Regenerate Bazel settings.`);
    }
  }

  const inProduction = file => {
    for (let directory = dirname(resolve(root, file)); directory !== dirname(directory); directory = dirname(directory)) {
      if (testRoots.has(directory)) return false;
      if (sourceRoots.has(directory)) return true;
    }
    return false;
  };
  let checkedFiles = 0;
  const importIndex = imports ? new ImportIndex() : null;
  const productionFiles = [...new Set(fileNames)].filter(file =>
    /\.(kt|java|xml)$/.test(file) && !/(^|\/)(testData|fixtures)(\/|$)/.test(file) &&
    inProduction(file) && existsSync(resolve(root, file)));
  // Two passes keep only names and unresolved imports, never all source texts
  // or all resolved import locations in memory.
  if (importIndex) {
    for (const file of productionFiles) {
      if (file.endsWith('.xml')) continue;
      const raw = read(file);
      importIndex.addSymbols(file, sourceCode(raw), raw);
    }
  }
  for (const file of productionFiles) {
    checkedFiles++;
    const raw = read(file);
    const text = file.endsWith('.xml') ? withoutXmlComments(raw) : sourceCode(raw);
    if (file.endsWith('.xml')) {
      for (const match of text.matchAll(/\b[\w:-]+\s*=\s*(["'])([^"']+)\1/g)) {
        if (isRemoved(match[2])) report(file, text.slice(0, match.index).split('\n').length, `Removed feature reference: ${match[2]}`);
      }
      for (const content of text.matchAll(/<content\b([^>]*)>([\s\S]*?)<\/content>/g)) {
        if (attributes(content[1]).namespace !== 'jetbrains') continue;
        for (const match of tags(content[2], 'module')) {
          const {name, loading} = attributes(match[0]);
          // Test content descriptors share their base module's JPS configuration.
          if (!name || modules.has(name.replace(/\._test$/, ''))) continue;
          const offset = content.index + content[0].indexOf('>') + 1 + match.index;
          const line = text.slice(0, offset).split('\n').length;
          if (loading === 'required' || loading === 'embedded') {
            report(file, line, `Unknown required content module: ${name}`);
          }
          else {
            // Wave skips unresolved optional content modules. Conditional loading
            // depends on the product layout, which this static check cannot evaluate.
            warnings.push({file, line, message: `Unresolved optional/conditional content module: ${name}`});
          }
        }
      }
    }
    else {
      importIndex?.addImports(file, text);
      for (const match of text.matchAll(/^[\t ]*import\s+(?:static\s+)?([\w.$*]+)/gm)) {
        if (isRemoved(match[1])) report(file, text.slice(0, match.index).split('\n').length, `Removed feature import: ${match[1]}`);
      }
    }
  }
  return {issues, warnings, moduleCount: modules.size, checkedFiles, generatedIndexPresent,
    importCandidates: importIndex?.missing() ?? [], importsChecked: imports};
}

async function main(args) {
  if (args.includes('--help')) {
    console.log(`Usage: node --max-old-space-size=256 build/check.mjs [--json] [--quick]
       node --max-old-space-size=256 build/check.mjs --compile [--dry-run]

Default: check module configuration, removed features, and internal class imports.
Import candidates are source-only hints: generated or library classes can be absent
from the index. Module classpaths, type checking, and runtime behavior are not checked.
--quick skips the source import index. --json includes all candidate locations.
--compile runs the low-memory Bazel build with --keep_going, without launching the IDE.
It checks the launch target and its dependencies, not all test targets or runtime paths.
Compilation logs are written under out/check; --dry-run only prints the command.
Static checks do not build, download, or modify files. Test fixtures are excluded.
Exit codes: 0 = no detected issues/candidates, 1 = issues or candidates to review,
2 = checker failed. Compile mode preserves Bazel's exit code.`);
    return;
  }
  if (args.includes('--compile')) {
    if (args.some(arg => !['--compile', '--dry-run'].includes(arg))) throw new Error('Compile mode only accepts --dry-run.');
    process.exitCode = await runCompilationCheck(defaultRoot, {dryRun: args.includes('--dry-run')});
    return;
  }
  if (args.some(arg => !['--json', '--quick'].includes(arg))) throw new Error('Unknown option. Use --help.');
  const files = execFileSync('git', ['-c', 'core.fsmonitor=false', 'ls-files', '-z', '--cached', '--others', '--exclude-standard'], {
    cwd: defaultRoot, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024,
  }).split('\0').filter(Boolean);
  const result = checkRepository(defaultRoot, files, {imports: !args.includes('--quick')});
  if (args.includes('--json')) console.log(JSON.stringify(result, null, 2));
  else {
    for (const issue of result.issues) console.error(`${issue.file}:${issue.line}: ${issue.message}`);
    console.log(`Checked ${result.moduleCount} modules and ${result.checkedFiles} source/resource files.`);
    if (!result.generatedIndexPresent) console.log('Skipped the generated module index: build/bazel-targets.json is absent.');
    if (result.warnings.length) console.log(`WARN: ${result.warnings.length} unresolved optional/conditional content modules. Use --json for details.`);
    for (const candidate of result.importCandidates.slice(0, 20)) {
      const first = candidate.locations[0];
      console.log(`REVIEW: ${candidate.name} (${candidate.locations.length} imports; ${first.file}:${first.line})`);
    }
    if (result.importCandidates.length) console.log(`REVIEW: ${result.importCandidates.length} internal classes not found in production sources. Use --json for all locations; use --compile to verify. Generated/library classes may be false positives.`);
    console.log(result.issues.length ? `FAIL: ${result.issues.length} issues.` :
      result.importCandidates.length ? 'REVIEW REQUIRED: import candidates remain. Compilation was not checked.' :
      'PASS: no issues in these static checks. Compilation was not checked.');
  }
  if (result.issues.length || result.importCandidates.length) process.exitCode = 1;
}

if (process.argv[1] && resolve(process.argv[1]) === scriptPath) {
  try {
    await main(process.argv.slice(2));
  }
  catch (error) {
    console.error(`Check failed: ${error.message}`);
    process.exitCode = 2;
  }
}
