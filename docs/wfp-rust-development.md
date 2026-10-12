# Rust compiler development in WfP

WfP is for contributors working on existing compiler implementations. Every
developer opens their own checkout; no developer-specific checkout path is a
default or required location. `node build/run.mjs` opens the welcome screen
without reopening a recent project. A path is used only if the developer passes
one explicitly. Use **Open** to select an existing source directory.

## Tools and Rust editing

In **Settings → Languages & Frameworks → WfP Toolchains**, select installed Cargo,
rustc, rust-analyzer and rustfmt executables or leave them empty for rustup/PATH
discovery. Relative overrides always resolve against the opened project's root,
even when a command runs in a nested Cargo workspace. Machine-specific selections
are stored in `.wfp/workspace.xml`.

**Enable WfP Rust language services** controls the optional rust-analyzer adapter.
Opening a Rust source file requests small, offline Cargo metadata in a background
project task, then starts one analyzer for its actual workspace root. No tool is
installed. Missing tools or invalid metadata provide setup guidance and leave
ordinary editing available. Apply changed tool selections to retry/restart.

The adapter uses platform LSP completion, definition navigation, semantic tokens
and diagnostics, and supplies the selected Cargo/rustc paths to the server.
Cargo metadata uses format version 1, `--no-deps --offline --locked`; dependency
resolution or downloads are never implicit. rust-analyzer is configured with
offline metadata, no build scripts, no procedural macros, no cache priming, and
no check-on-save. Consequently macro expansion/build-script-derived information
and compiler diagnostics requiring a Cargo check are limited. Run an explicit
Cargo check when needed.

An enabled Rust plugin (`org.rust`, `com.jetbrains.rust`, or `com.jetbrains.rustrover`)
takes precedence. An active external LSP provider for Rust also takes precedence;
if it starts later, WfP stops its Rust client. Disable WfP's checkbox when using
an external integration that WfP cannot identify. Wave `.wave` language services
are independent. The platform owns watched LSP document roots and stops clients
on project close; the project execution scope also owns the server process.

## Cargo packages and targets

**Tools → Refresh Cargo Workspace** finds the selected file's nearest manifest.
If none exists, it offers manifests found in a bounded directory scan (depth 6,
5000 entries, skipping `target`, `.git`, `.idea`, `.wfp` and directory links).
Select a manifest when more than one is found. The target popup opens the source
entry for a package target. **Tools → Open Cargo Target Source** reuses the
loaded model without launching Cargo.

Metadata represents root-package and virtual workspaces, package identities,
target names/kinds, Rust editions and the actual Cargo output directory.
Manifest, lockfile, rust-toolchain and Cargo configuration changes invalidate
the model and refresh previously requested workspaces offline. Malformed data
does not replace a model with partially parsed packages. The standard `target`
directory for a Cargo root and loaded custom output directories are excluded
from source indexing. A missing/locked dependency failure is reported; prepare
dependencies yourself or use an explicit online command.

## Build, check and test

In **Run → Edit Configurations**, add a **Cargo** configuration. Select `build`,
`check` or `test`, and set the manifest relative to the opened project or as an
absolute path. **Refresh Cargo Workspace** in the editor populates package and
target choices and shows the workspace details without compiling.

Configure package, target kind/name, profile, feature flags, working directory,
test filter and test-program arguments. Empty package/target/profile fields keep
Cargo's defaults; an empty working directory uses the resolved workspace root.
Environment overrides are one `NAME=value` per line. Test-program arguments
support quoting and are passed as separate arguments after `--`, without a shell.
They and the test filter are valid only for `test`.

Execution starts only on **Run**. Offline mode is enabled by default; clear it
explicitly to permit Cargo dependency downloads. `--locked` is always retained,
so update dependency locks separately when necessary. The selected rustc is
passed through `RUSTC`. Cargo's build-script/runner configuration still applies
to these explicit user-requested builds and tests. Interactive test stdin and
native debugging are outside this configuration's scope.

The Run window exposes stop, output and final exit status. Success, nonzero exit,
launch failure, timeout and cancellation are distinct. The timeout defaults to
600 seconds and is configurable. Shared execution retains at most 128K characters
for a command result; the platform console uses its own cyclic buffer.
Metadata and formatting have separate 8M-character capture limits and reject truncated results.
An optional log path saves complete stdout/stderr chunks in reception order,
overwriting that selected file on each run; leave it empty to avoid file output.

On Linux, execution requires util-linux `setsid` and uses a new process group,
including cleanup when the parent exits early. Cancellation and project disposal
terminate the group and tracked children. Other local hosts use platform tree
termination and tracked process handles; the strongest early-parent-exit guarantee
is Linux-only. Deliberately daemonized children that create their own sessions
are outside the owned process group. Remote, WSL and container toolchains are
not supported by this adapter.

Cargo `compiler-message` JSON is rendered in the Run console with links for
primary, secondary and macro-expansion spans. Relative paths use Cargo's workspace
root; Rust Unicode columns are converted into editor offsets when clicked.
Missing files do not prevent reading the message. Unknown events and malformed
JSON remain raw output. These diagnostics are confined to the Run console and
are not republished as a second set of editor annotations beside rust-analyzer.

## Explicit whole-file formatting

Use **Tools → Format Rust File with Rustfmt** or the editor context menu for `.rs`
files. The action derives the edition from Cargo targets and searches rustfmt
configuration from the source directory. It sends the current, possibly unsaved
document through stdin and captures stdout, without handing the source file to
rustfmt for modification. CRLF output is normalized to the editor's internal LF;
the IDE retains the file's configured line separator on save.

The action applies successful output through one undoable command only if the
document version still matches. Failure, cancellation, timeout, oversized output
or concurrent edits leave the document unchanged. WfP LSP formatting is disabled
so this explicit action has one formatter owner. Installed Rust plugins keep
their own formatting ownership. Format-on-save and range formatting are outside
this action's scope.

## Manual verification

Builds, tests, tool execution and IDE launches are run by the developer. They were
not run while implementing this change. IML dependencies and their Bazel entries
are both updated; regenerate and inspect them before compilation:

```sh
./build/jpsModelToBazel.cmd
./tests.cmd --module intellij.wave --test 'dev.wavelang.intellij.wave.cargo.*Test;dev.wavelang.intellij.wave.rust.*Test;dev.wavelang.intellij.wave.toolchain.*Test'
```

After your normal manual build:

1. Launch without a project argument. Verify the welcome screen and Open flow,
   then open two different checkouts and confirm each has independent tool settings.
2. Use a tiny root-package workspace, virtual workspace and nested package. Refresh
   metadata, choose packages/targets, change a manifest, and inspect exclusions.
   Verify no builds/downloads on open or save and that missing tools remain actionable.
3. Check completion, definition navigation and diagnostics with an installed analyzer.
   For controlled responses, make `plugins/wave/testData/rust/fake-analyzer.mjs`
   executable and select it as rust-analyzer in a disposable tiny Rust workspace
   (requires Node on PATH). It supplies `fixture_symbol`, a definition at line 1,
   and a controlled warning; it never invokes a compiler. Switch back afterward.
4. Install/enable an external Rust provider and confirm only one editing provider
   remains active. Close the project and confirm its analyzer exits.
5. Execute selected Cargo build/check/test configurations. Check profiles/features,
   quoted test arguments, paths containing spaces/Unicode, diagnostic links,
   malformed messages, cancellation, timeout, early parent exit and project close.
6. Format an unsaved Rust file with edition 2024 and nested rustfmt configuration.
   Verify Undo, CRLF saving, tool failure and editing while formatting is in flight.
7. Use keyboard-only navigation through all settings, editable selectors, buttons,
   Run controls and target popups. Check field/result labels with a screen reader.
   Labels and result areas are linked in code; actual keyboard/screen-reader
   verification remains manual.

The fake executable/unit fixtures cover command boundaries, serialization,
metadata variants, bounded output, process status/cleanup, Unicode diagnostics,
stdin and formatter concurrency decisions. They are regression inputs, not claims
that compilation or runtime acceptance passed. IDE `lint_files` was unavailable.

Contracts: [Cargo metadata](https://doc.rust-lang.org/cargo/commands/cargo-metadata.html),
[Cargo JSON messages](https://doc.rust-lang.org/cargo/reference/external-tools.html),
[rust-analyzer configuration](https://rust-analyzer.github.io/book/configuration.html),
[rust-analyzer tool selection](https://github.com/rust-lang/rust-analyzer/blob/master/crates/toolchain/src/lib.rs),
[rustfmt stdin/configuration](https://github.com/rust-lang/rustfmt/blob/master/src/bin/main.rs).
