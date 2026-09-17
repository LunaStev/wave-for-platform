# Checking Wave for Platform

## Static checks (no compilation)

```bash
node --max-old-space-size=256 build/check.mjs
node --max-old-space-size=256 build/check.mjs --json > /tmp/wave-check.json
```

Checks module configuration, known removed features, and imports of internal classes
against registered production Java/Kotlin sources, including test support modules
compiled as production code. The import index covers `com.intellij`, `com.jetbrains`,
`org.jetbrains.idea`, and `org.jetbrains.plugins`. It ignores ordinary test roots and
test fixtures. `--quick` skips the import index.

`REVIEW` means a class was not found in this source index. A dependency JAR, generated
code, or language syntax the scanner does not understand may supply it. These are
candidates for investigation, not confirmed compiler errors. All locations are in
the JSON output. No missing class is automatically deleted or replaced with a stub.

The scanner does not resolve module classpaths, same-package references, wildcard
package imports, members, overloads, or types. It cannot guarantee compilation or
correct behavior. Exit 1 means known issues or import candidates remain.

## Collect compiler errors without launching the IDE

```bash
node --max-old-space-size=256 build/check.mjs --compile --dry-run
node --max-old-space-size=256 build/check.mjs --compile
```

Compile mode runs Bazel `build --keep_going --config=low-memory //build:idea_community`.
It continues independent work after failures so one invocation can report several
broken modules. Errors within targets blocked by failed dependencies may only appear
after those dependencies are fixed. It does not run tests or the IDE, and bypasses
the static scanner so uncertain candidates cannot prevent a real compiler check.

Each run streams output and saves `out/check/compile-*/build.log` plus `summary.json`.
The summary includes the exit code, failed compile targets, and up to 200 diagnostic
lines; the full log is not truncated. The Node process does not retain the full log
in memory. Bazel's exit code is preserved.

The low-memory profile uses one job and one compiler worker, with a 1536 MiB Bazel
heap and 4 GiB compiler heap. These are separate limits, not a total memory cap.
Compilation can download dependencies and consume substantial time and memory.
Run it manually when ready. A successful build still does not validate startup,
plugin loading, or runtime behavior.
