package dev.wavelang.intellij.wave.toolchain

import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.Key
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.spawnProcess
import com.intellij.util.EnvironmentUtil
import dev.wavelang.intellij.wave.WaveBundle
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import java.nio.file.Path

data class ToolCommand(
  val executable: Path,
  val arguments: List<String>,
  val workingDirectory: Path,
  val environment: Map<String, String>,
)

internal fun interface ToolProbe {
  fun run(command: ToolCommand, indicator: ProgressIndicator): ProcessOutput
}

/** Small, explicit version probes. The shared build/run lifecycle belongs to a separate integration. */
internal object LocalToolProbe : ToolProbe {
  override fun run(command: ToolCommand, indicator: ProgressIndicator): ProcessOutput = runBlockingMaybeCancellable {
    indicator.checkCanceled()
    val processScope = this
    val process = withTimeout(5_000) {
      localEel.exec.spawnProcess(command.executable.asEelPath())
        .args(command.arguments)
        .workingDirectory(command.workingDirectory.asEelPath())
        .env(command.environment)
        .scope(processScope)
        .eelIt()
        .convertToJavaProcess()
    }
    val handler = object : CapturingProcessHandler(process, Charsets.UTF_8, command.executable.toString()) {
      override fun createProcessAdapter(processOutput: ProcessOutput): CapturingProcessAdapter {
        return object : CapturingProcessAdapter(processOutput) {
          private var remaining = 16 * 1024

          @Synchronized
          override fun addToOutput(text: String, outputType: Key<*>) {
            val retained = text.take(remaining)
            remaining -= retained.length
            super.addToOutput(retained, outputType)
          }
        }
      }
    }
    try {
      handler.runProcessWithProgressIndicator(indicator, 5_000, true)
    }
    finally {
      if (!handler.isProcessTerminated) handler.destroyProcess()
    }
  }
}

/** Local IDE-host tools only. No installation, build, or project-open activity is performed here. */
internal class ToolchainResolver(
  private val projectRoot: Path,
  private val environment: Map<String, String> = EnvironmentUtil.getEnvironmentMap(),
  private val probe: ToolProbe = LocalToolProbe,
) {
  private val toolEnvironment = environment + ("RUSTUP_AUTO_INSTALL" to "0")

  private fun path(value: String, base: Path = projectRoot): Path {
    val parsed = Path.of(value)
    val resolved = (if (parsed.isAbsolute) parsed else base.resolve(parsed)).normalize().toAbsolutePath()
    require(resolved.getEelDescriptor() == LocalEelDescriptor && !resolved.toString().startsWith("\\\\")) {
      WaveBundle.message("toolchains.local.only")
    }
    return resolved
  }

  private fun executable(value: String, base: Path = projectRoot): Path {
    val resolved = path(value, base)
    require(Files.isRegularFile(resolved) && Files.isExecutable(resolved)) {
      WaveBundle.message("toolchains.not.executable", resolved)
    }
    return resolved
  }

  private fun onPath(name: String): Path? {
    val searchPath = environment.entries.firstOrNull { it.key.equals("PATH", ignoreCase = true) }?.value.orEmpty()
    for (directory in searchPath.split(System.getProperty("path.separator"))) {
      if (directory.isBlank()) continue
      val root = Path.of(directory)
      // Do not accidentally execute a project file through an empty or relative PATH entry.
      if (!root.isAbsolute) continue
      for (fileName in listOf(name, "$name.exe")) {
        val candidate = root.resolve(fileName)
        if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return executable(candidate.toString())
      }
    }
    return null
  }

  fun rustCommand(tool: CompilerTool, settings: RustToolchain, arguments: List<String>, indicator: ProgressIndicator): ToolCommand {
    require(tool.rust)
    indicator.checkCanceled()
    val directory = path(projectRoot.toString())
    val override = when (tool) {
      CompilerTool.CARGO -> settings.cargo
      CompilerTool.RUSTC -> settings.rustc
      CompilerTool.RUST_ANALYZER -> settings.rustAnalyzer
      CompilerTool.RUSTFMT -> settings.rustfmt
      else -> error("Not a Rust tool: $tool")
    }
    val rustEnvironment = if (settings.toolchain.isBlank()) toolEnvironment
    else toolEnvironment + ("RUSTUP_TOOLCHAIN" to settings.toolchain)
    val selected = if (override.isNotBlank()) executable(override)
    else {
      val rustup = onPath("rustup")
      if (rustup != null) {
        // `which` resolves installed binaries in this project's rust-toolchain/override context without installing them.
        val selection = if (settings.toolchain.isBlank()) emptyList() else listOf("--toolchain", settings.toolchain)
        val lookup = ToolCommand(rustup, listOf("which") + selection + tool.executableName, directory, rustEnvironment)
        val output = probe.run(lookup, indicator)
        requireSuccessful(output, tool.executableName)
        executable(output.stdout.trim())
      }
      else {
        require(settings.toolchain.isBlank()) { WaveBundle.message("toolchains.rustup.required") }
        onPath(tool.executableName) ?: throw IllegalArgumentException(WaveBundle.message("toolchains.missing", tool.executableName))
      }
    }
    return ToolCommand(selected, arguments.toList(), directory, rustEnvironment)
  }

  fun ecosystemCommand(tool: CompilerTool, profile: EcosystemProfile, arguments: List<String>): ToolCommand {
    require(!tool.rust)
    val directory = if (profile.workingDirectory.isBlank()) path(projectRoot.toString()) else path(profile.workingDirectory)
    require(Files.isDirectory(directory)) { WaveBundle.message("toolchains.invalid.directory", directory) }
    val selected = ecosystemExecutable(tool, profile)
    // A Vex child must use exactly the same compiler selection as a direct wavec command.
    val childEnvironment = if (tool == CompilerTool.VEX) {
      toolEnvironment + ("VEX_WAVEC" to ecosystemExecutable(CompilerTool.WAVEC, profile).toString())
    }
    else toolEnvironment
    return ToolCommand(selected, arguments.toList(), directory, childEnvironment)
  }

  private fun ecosystemExecutable(tool: CompilerTool, profile: EcosystemProfile): Path {
    val (override, root) = when (tool) {
      CompilerTool.WAVEC -> profile.wavec to profile.waveRoot
      CompilerTool.WHALE -> profile.whale to profile.whaleRoot
      CompilerTool.VEX -> profile.vex to profile.vexRoot
      else -> error("Not an ecosystem tool: $tool")
    }
    val sourceRoot = if (root.isBlank()) path(projectRoot.toString()) else path(root)
    require(Files.isDirectory(sourceRoot)) { WaveBundle.message("toolchains.invalid.directory", sourceRoot) }
    if (override.isNotBlank()) return executable(override, sourceRoot)
    // A repository selection does not imply a debug/release build or silently select a stale binary.
    return onPath(tool.executableName) ?: throw IllegalArgumentException(WaveBundle.message("toolchains.missing", tool.executableName))
  }

  fun inspect(state: ToolchainState, indicator: ProgressIndicator): String {
    val profile = state.activeProfile()
    return CompilerTool.entries.joinToString("\n\n") { tool ->
      indicator.checkCanceled()
      try {
        val command = if (tool.rust) rustCommand(tool, state.rust, listOf("--version"), indicator)
        else {
          requireNotNull(profile) { WaveBundle.message("toolchains.no.profile") }
          // Capability/version protocols differ. Until #51, Whale/Vex are validated as executable paths only.
          ecosystemCommand(tool, profile, emptyList())
        }
        if (!tool.rust) {
          WaveBundle.message("toolchains.path.resolved", tool.executableName, command.executable)
        }
        else {
          val output = probe.run(command, indicator)
          requireSuccessful(output, tool.executableName)
          val version = output.stdout.trim().ifEmpty { output.stderr.trim() }
          require(version.startsWith(tool.executableName + " ")) { WaveBundle.message("toolchains.unexpected.version", version) }
          WaveBundle.message("toolchains.version.resolved", tool.executableName, command.executable, version)
        }
      }
      catch (e: Exception) {
        ProgressManager.checkCanceled()
        indicator.checkCanceled()
        WaveBundle.message("toolchains.failed", tool.executableName, e.message ?: e.javaClass.simpleName)
      }
    }
  }

  private fun requireSuccessful(output: ProcessOutput, tool: String) {
    require(!output.isCancelled && !output.isTimeout && output.exitCode == 0) {
      WaveBundle.message("toolchains.probe.failed", tool, output.exitCode, output.stderr.trim().take(1024))
    }
  }
}
