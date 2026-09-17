import {spawn} from 'node:child_process';
import {statSync} from 'node:fs';
import {constants} from 'node:os';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');

function main(args) {
  if (args.includes('--help')) {
    console.log(`Usage: node build/run.mjs [--dry-run] [project-directory]

Build and launch Wave for Platform using the low-memory Bazel profile.
Without a project directory, opens the welcome screen without reopening recent projects.
Relative project paths are resolved from your current working directory.

Bazel heap: 1536 MiB. Compiler heap: 4 GiB. IDE heap: 2 GiB.
These are separate JVM limits, not a limit on total system memory.
--dry-run prints the command without building or launching the IDE.
The static checker is separate: node --max-old-space-size=256 build/check.mjs`);
    return;
  }

  const paths = args.filter(arg => arg !== '--dry-run');
  if (paths.length > 1 || paths.some(arg => arg.startsWith('-'))) {
    throw new Error('Expected at most one project directory. Use --help for usage.');
  }
  const project = paths.length ? resolve(paths[0]) : null;
  if (project && !statSync(project, {throwIfNoEntry: false})?.isDirectory()) {
    throw new Error(`Project directory does not exist: ${project}`);
  }
  if (process.platform !== 'linux' && process.platform !== 'darwin') {
    throw new Error('This launcher currently supports Linux and macOS.');
  }

  // bazel run performs the build first and launches only when it succeeds.
  const bazelArgs = [
    resolve(root, 'bazel.cmd'),
    '--batch',
    '--host_jvm_args=-Xmx1536m',
    'run',
    '--config=low-memory',
    '//build:idea_community',
    '--',
    '--jvm_flag=-Xmx2g',
    ...(project ? [project] : ['dontReopenProjects']),
  ];
  if (args.includes('--dry-run')) {
    console.log(JSON.stringify({cwd: root, command: 'bash', args: bazelArgs}, null, 2));
    return;
  }

  console.log(`Building and launching Wave for Platform${project ? `: ${project}` : ' (welcome screen)'}`);
  const child = spawn('bash', bazelArgs, {cwd: root, stdio: 'inherit'});
  const interrupt = () => child.kill('SIGINT');
  const terminate = () => child.kill('SIGTERM');
  process.on('SIGINT', interrupt);
  process.on('SIGTERM', terminate);
  child.on('error', error => {
    console.error(`Could not launch Bazel: ${error.message}`);
    process.exitCode = 1;
  });
  child.on('close', (code, signal) => {
    process.removeListener('SIGINT', interrupt);
    process.removeListener('SIGTERM', terminate);
    process.exitCode = code ?? (signal ? 128 + (constants.signals[signal] ?? 1) : 1);
  });
}

try {
  main(process.argv.slice(2));
}
catch (error) {
  console.error(`Run failed: ${error.message}`);
  process.exitCode = 1;
}
