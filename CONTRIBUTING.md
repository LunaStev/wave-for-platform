# Contributing to Wave for Platform (WfP)

Thank you for your interest in contributing to the Wave for Platform (WfP) repository! WfP is an IDE for developing the Wave compiler, forked from IntelliJ IDEA. 

## Issues and Pull Requests
- **Keep it focused**: Submit pull requests for reproducible bugs or pre-discussed features. Keep changes focused on a single issue.
- **Reproducible bug reports**: Ensure bug reports include detailed steps to reproduce the issue, expected versus actual behavior, and relevant environment details (OS, version).
- **Good first issues**: Look for issues tagged with `#patch_welcome` or `good first issue` if you are looking for an entry point into the codebase.

## Repository Layout
The primary WfP plugin code resides in `plugins/wave`:
- **Source Code**: `plugins/wave/src/main/kotlin/` and `plugins/wave/src/main/java/`
- **Resources** (icons, plugin.xml, properties): `plugins/wave/src/main/resources/`
- **Tests**: `plugins/wave/src/test/kotlin/` and `plugins/wave/src/test/testData/`

**Note**: `*.iml` files are the source of truth for the project model. If you modify an `.iml` file or project structure, you must regenerate the Bazel metadata by running `./build/jpsModelToBazelCommunityOnly.cmd`. Do not manually edit `BUILD.bazel` files.

## Toolchains and Prerequisites
- **Supported Launcher Hosts**: Tools and toolchains execute exclusively on the local IDE host. Remote, container, and WSL toolchains are **not** supported by WfP.
- **Prerequisite Discovery**: The IDE discovers installed tools (e.g., Cargo, Rustc) via `rustup` or your system `PATH`. WfP checks never install or build tools for you automatically; you must leave paths empty to auto-discover them or explicitly provide them.

## Checks and Compilation
We provide static checks and opt-in compilation via Node.js scripts:

- **Static Checks** (`node --max-old-space-size=256 build/check.mjs`): 
  Runs quickly and performs static checks for module configuration, removed features, and internal class imports without invoking the compiler.
- **Opt-in Compilation** (`--compile`): 
  Runs `node --max-old-space-size=256 build/check.mjs --compile`. This runs a low-memory Bazel build (`//build:idea_community`) to verify that the modified modules actually compile. It does not run tests or launch the IDE. Use this when you are ready to validate compilation.
- **Dry Run** (`--dry-run`): 
  When combined with `--compile` (`node build/check.mjs --compile --dry-run`), this only prints the Bazel command without executing it. Use this to inspect the target configuration.

## Testing
Run targeted tests using the cross-platform `tests.cmd` script by specifying the module and fully qualified test name. 

Example targeted test for WfP on Unix/macOS:
```bash
./tests.cmd --module intellij.wave --test dev.wavelang.intellij.wave.highlight.WaveLexerTest
```

Example targeted test for WfP on Windows:
```cmd
tests.cmd --module intellij.wave --test dev.wavelang.intellij.wave.highlight.WaveLexerTest
```
*(Simple class names or unqualified patterns do not match; always specify the exact module and full class name.)*

## Memory and Runtime Expectations
WfP is a large repository. When running the opt-in `--compile` mode, the low-memory profile uses one job and one compiler worker with a **1536 MiB Bazel heap** and a **4 GiB compiler heap**. 
Compilation can download heavy dependencies and consume substantial time and memory. A successful compilation does not guarantee correct runtime behavior, so test your changes manually. 

## Debugging Workflows
For information on debugging the compiler, generated Wave programs, or using compiler dump flags, please see the [Compiler-Development Debugging Workflows](DEBUGGING.md) guide.
