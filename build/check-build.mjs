import {spawn} from 'node:child_process';
import {closeSync, mkdirSync, mkdtempSync, openSync, writeFileSync, writeSync} from 'node:fs';
import {constants} from 'node:os';
import {join, resolve} from 'node:path';

export function compilationCommand(root) {
  return {command: 'bash', args: [
    resolve(root, 'bazel.cmd'), '--batch', '--host_jvm_args=-Xmx1536m',
    'build', '--config=low-memory', '--keep_going', '--color=no', '--curses=no',
    '//build:idea_community',
  ]};
}

export class BuildDiagnostics {
  pending = new Map();
  targets = new Set();
  samples = [];

  add(stream, chunk) {
    const lines = ((this.pending.get(stream) ?? '') + chunk).split(/\r?\n/);
    this.pending.set(stream, lines.pop().slice(-16384));
    for (const line of lines) this.line(line);
  }

  line(line) {
    const target = line.match(/\bcompile\s+(\/\/[^\s]+).*\bfailed:/)?.[1];
    if (target) this.targets.add(target);
    if (this.samples.length < 200 && /^(ERROR:|Kotlinc Runner: Error:|Javac Runner: Error:)/.test(line)) {
      this.samples.push(line.slice(0, 4096));
    }
  }

  finish() {
    for (const line of this.pending.values()) this.line(line);
    return {failedCompileTargets: [...this.targets].sort(), errorSamples: this.samples};
  }
}

export async function runCompilationCheck(root, {dryRun = false, output = process.stdout, errors = process.stderr} = {}) {
  const command = compilationCommand(root);
  if (dryRun) {
    output.write(`${JSON.stringify({cwd: root, ...command}, null, 2)}\n`);
    return 0;
  }
  if (!['linux', 'darwin'].includes(process.platform)) throw new Error('Compilation checking currently supports Linux and macOS.');
  const parent = join(root, 'out/check');
  mkdirSync(parent, {recursive: true});
  const directory = mkdtempSync(join(parent, 'compile-'));
  const logPath = join(directory, 'build.log');
  const summaryPath = join(directory, 'summary.json');
  const log = openSync(logPath, 'w');
  const diagnostics = new BuildDiagnostics();
  output.write(`Checking compilation with --keep_going; the IDE will not launch.\nLog: ${logPath}\n`);
  let child;
  try {
    child = spawn(command.command, command.args, {cwd: root, stdio: ['inherit', 'pipe', 'pipe']});
    const interrupt = () => child.kill('SIGINT');
    const terminate = () => child.kill('SIGTERM');
    process.on('SIGINT', interrupt);
    process.on('SIGTERM', terminate);
    let failure;
    const relay = (source, destination, name) => {
      source.setEncoding('utf8');
      source.on('data', text => {
        try {
          writeSync(log, text);
          diagnostics.add(name, text);
          if (!destination.write(text)) {
            source.pause();
            destination.once('drain', () => source.resume());
          }
        }
        catch (error) {
          failure = error;
          child.kill('SIGTERM');
        }
      });
    };
    relay(child.stdout, output, 'stdout');
    relay(child.stderr, errors, 'stderr');
    const result = await new Promise(resolveResult => {
      child.on('error', error => { failure = error; });
      child.on('close', (code, signal) => resolveResult({code, signal}));
    });
    process.removeListener('SIGINT', interrupt);
    process.removeListener('SIGTERM', terminate);
    const exitCode = failure ? 2 : result.code ?? (128 + (constants.signals[result.signal] ?? 1));
    const summary = {exitCode, signal: result.signal, failure: failure?.message, ...diagnostics.finish(), logPath,
      scope: '//build:idea_community and dependencies; tests and runtime behavior were not checked'};
    writeFileSync(summaryPath, `${JSON.stringify(summary, null, 2)}\n`);
    output.write(`\n${exitCode === 0 ? 'Compilation passed.' : `Compilation did not pass (exit ${exitCode}).`}\n`);
    for (const target of summary.failedCompileTargets) output.write(`FAILED: ${target}\n`);
    output.write(`Summary: ${summaryPath}\nFull log: ${logPath}\n`);
    if (failure) errors.write(`Check failed: ${failure.message}\n`);
    return exitCode;
  }
  finally {
    closeSync(log);
  }
}
