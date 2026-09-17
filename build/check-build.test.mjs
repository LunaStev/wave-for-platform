import assert from 'node:assert/strict';
import {mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {Writable} from 'node:stream';
import test from 'node:test';
import {BuildDiagnostics, compilationCommand, runCompilationCheck} from './check-build.mjs';

test('compile mode continues after errors using the low-memory build, never run', () => {
  const {args} = compilationCommand('/tmp/example');
  assert.ok(args.includes('build'));
  assert.ok(args.includes('--keep_going'));
  assert.ok(args.includes('--config=low-memory'));
  assert.ok(args.includes('--host_jvm_args=-Xmx1536m'));
  assert.ok(!args.includes('run'));
});

test('diagnostics tolerate split chunks and keep only bounded samples', () => {
  const diagnostics = new BuildDiagnostics();
  diagnostics.add('stderr', 'ERROR: /tmp/a: compile //one:one');
  diagnostics.add('stdout', 'Kotlinc Runner: Error: missing class\n');
  diagnostics.add('stderr', ' (kt: 1) failed: (Exit -1)\n');
  for (let i = 0; i < 300; i++) diagnostics.add('stderr', 'ERROR: sample\n');
  diagnostics.add('stderr', 'ERROR: /tmp/b: compile //two:two failed:');
  const result = diagnostics.finish();
  assert.deepEqual(result.failedCompileTargets, ['//one:one', '//two:two']);
  assert.equal(result.errorSamples.length, 200);
});

test('dry-run does not create output or require a Bazel installation', async t => {
  const root = mkdtempSync(join(tmpdir(), 'wave-build-check-'));
  t.after(() => rmSync(root, {recursive: true, force: true}));
  const output = new Writable({write(chunk, encoding, done) { done(); }});
  assert.equal(await runCompilationCheck(root, {dryRun: true, output}), 0);
  assert.deepEqual(readdirSync(root), []);
});

test('saves all output and preserves failure status using a fake Bazel script', async t => {
  const root = mkdtempSync(join(tmpdir(), 'wave-build-check-'));
  t.after(() => rmSync(root, {recursive: true, force: true}));
  // This is a fixture script: no real Bazel, compiler, downloads, or IDE.
  writeFileSync(join(root, 'bazel.cmd'), `printf 'normal output\\n'
printf 'ERROR: /a: compile //one:one failed: (Exit 1)\\n' >&2
printf 'ERROR: /b: compile //two:two failed: (Exit 1)\\n' >&2
exit 7
`);
  const output = new Writable({write(chunk, encoding, done) { done(); }});
  assert.equal(await runCompilationCheck(root, {output, errors: output}), 7);
  const directory = join(root, 'out/check', readdirSync(join(root, 'out/check'))[0]);
  const summary = JSON.parse(readFileSync(join(directory, 'summary.json'), 'utf8'));
  assert.equal(summary.exitCode, 7);
  assert.deepEqual(summary.failedCompileTargets, ['//one:one', '//two:two']);
  const log = readFileSync(join(directory, 'build.log'), 'utf8');
  assert.match(log, /normal output/);
  assert.match(log, /\/\/one:one/);
  assert.match(log, /\/\/two:two/);
});
