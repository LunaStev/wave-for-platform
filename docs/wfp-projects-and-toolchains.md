# WfP project settings and compiler toolchains

WfP opens existing source directories and stores project settings in `.wfp/`.
The New Project wizard is not required. Opening a project does not build a
compiler, install a toolchain, or run the tool checks described below.

## Existing projects

On first open, if `.idea/` exists and `.wfp/` does not, WfP copies the old
settings into a temporary sibling directory, then renames the completed copy
to `.wfp/`. The original `.idea/` remains intact. References into `.idea/` in
copied XML/module files are adjusted to point into `.wfp/`.

An existing `.wfp/` always wins, even if it is empty. The two stores are never
merged. A failed or cancelled import removes its temporary copy and does not
publish a partial `.wfp/`. Non-regular files, including symbolic links inside
the old settings directory, stop the import rather than allowing later saves
to write through a link into the original store. Resolve those links before
retrying. A symbolic link used as the `.idea` directory itself is not imported.

After migration, edits made by another IDE to `.idea/` are not synchronized.
The copied `.wfp/.gitignore` preserves existing per-user exclusions; review
which settings you want to share before adding `.wfp/` to Git.

The WfP source repository's checked-in `.idea/` remains the input to its
JPS/Bazel build tooling. JPS can read both layouts, preferring `.wfp/` when
both exist. This change does not rename Java/Kotlin package names or license
and copyright notices.

## Settings → Languages & Frameworks → WfP Toolchains

Settings are project-specific and saved in `.wfp/workspace.xml`. This is a
machine-specific file, not a toolchain definition to commit for other users.
The existing Wave language-server selection is independent and unchanged.

### Rust tools

Set paths for `cargo`, `rustc`, `rust-analyzer`, and optional `rustfmt`.
Each explicit executable takes precedence. Relative Rust executable paths
are resolved against the project root.

For an empty path, WfP finds `rustup` on PATH and asks `rustup which` for an
installed binary with the project root as its working directory. A nonempty
toolchain override is passed explicitly; otherwise rustup's project context,
including `rust-toolchain.toml`, directory overrides and the inherited
`RUSTUP_TOOLCHAIN`, applies. A failed rustup lookup is reported and does not
silently switch to another toolchain.

If rustup is absent, discovery uses PATH. A named toolchain without rustup
requires explicit executable paths. Relative or empty PATH entries are
ignored. [`RUSTUP_AUTO_INSTALL=0`](https://rust-lang.github.io/rustup/environment-variables.html)
is supplied to child processes. WfP never runs
`rustup install`, `rustup component add`, Cargo builds, or downloads.

**Check Tools** runs Rust version probes in the background, with cancellation,
a five-second execution limit per probe, and bounded captured output. It
reports missing tools, failed execution, and unexpected version responses.
It checks the values currently entered without saving them; Apply/OK saves.
This prepares the settings needed by rust-analyzer/Cargo integration; it does
not yet add Rust editing or Cargo run configurations.

### Wave, Whale and Vex profiles

Create named profiles and select the active one. Each profile separates:

- The executable for each tool, including local development binaries.
- The source repository root for each tool.
- The command working directory, defaulting to the opened project root.
- A target identifier for future tool-specific run configurations.

Source roots and working directories may be absolute or project-relative.
An explicit ecosystem executable may be absolute or relative to its tool's
source root (or the project root when no source root is selected). Empty
executable paths use PATH. Selecting a source repository does not implicitly
build or choose `target/debug` versus `target/release`; select that binary
explicitly. Paths are literal values, without shell syntax or `~` expansion.

The command preparation API keeps arguments as separate list elements and
sets `VEX_WAVEC` to exactly the compiler selected for direct Wave commands,
overriding an inherited `VEX_WAVEC`. Missing compiler selection prevents
preparing a Vex command instead of silently choosing a different compiler.
Target fields are stored as metadata; they are not blindly appended as flags
to tools with different command-line interfaces.

Ecosystem checks validate executable paths only. They explicitly report that
capabilities are unverified: a path check does not prove a compatible version,
Whale linking support, or a Wave Whale backend. Tool-specific capability
probes belong to #51. EEL starts the explicit checks on the local IDE host;
remote, WSL and container toolchains are outside this implementation's scope.

## Product stability

WfP no longer bundles Configuration Script or injects it into the development
launcher startup classpath. The platform's `PlatformLangProjectStoreFactory`
remains the project store factory. Experimental YAML/JSON settings overlays
are not part of the WfP product. Services are not made globally overridable.

The unused Kotlin EAP notification registration is removed. The Kotlin
updater and its missing resource bundle are not restored.

## Validation to run manually

No build, tests, IDE launch, or tool probes were run during this change.
Module dependency additions are reflected in both the IML and Bazel file;
regenerate and review the generated diff before compilation:

```sh
./build/jpsModelToBazel.cmd
node --test build/wfp-notifications.test.mjs
./tests.cmd --module intellij.wave --test 'dev.wavelang.intellij.wave.toolchain.*Test'
./tests.cmd --module intellij.platform.configurationStore.tests --test 'com.intellij.configurationStore.LegacyProjectStoreMigrationTest;com.intellij.configurationStore.ProjectStoreTest'
./tests.cmd --module intellij.idea.community.build.tests --test org.jetbrains.intellij.build.WfpProductCompositionTest
```

Manual IDE checks after your normal build:

1. Open a fresh source folder, save, close and reopen it. Settings and the
   generated module should live in `.wfp/`, without creating `.idea/`.
2. Open a disposable copy of a legacy project with modules, a project name,
   editor state and run configurations. Verify import, original preservation,
   save/reopen behavior, and no second import once `.wfp/` exists.
3. Open notification settings and check logs for missing bundles or
   `ProjectStoreFactory` override conflicts.
4. Save two toolchain profiles, switch between them, reopen settings and the
   project, and check explicit binaries, missing tools, and paths with spaces
   and Unicode. Cancel a slow probe. Verify no build/download on project open.
5. Navigate the settings panel by keyboard and read field labels and check
   results with a screen reader. Labels are linked to fields and results are
   focusable plain text; runtime accessibility validation remains manual.

Static whitespace/diff review is not evidence of successful compilation or
runtime verification. IDE `lint_files` was unavailable in the editing session.
