package dev.wavelang.intellij.wave.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class WaveLspServerSupportProvider : LspServerSupportProvider {
  override fun fileOpened(
    project: Project,
    file: VirtualFile,
    serverStarter: LspServerSupportProvider.LspServerStarter,
  ) {
    if (file.extension.equals("wave", ignoreCase = true)) {
      serverStarter.ensureServerStarted(WaveLspServerDescriptor(project))
    }
  }
}

private class WaveLspServerDescriptor(
  private val waveProject: Project,
) : ProjectWideLspServerDescriptor(waveProject, "Wave") {
  override fun isSupportedFile(file: VirtualFile): Boolean =
    file.extension.equals("wave", ignoreCase = true)

  override fun createCommandLine(): GeneralCommandLine {
    val configured = System.getenv("WAVE_AGAPE_PATH")?.trim().orEmpty()
    val executable = configured.ifEmpty {
      BundledWaveAgape.resolve() ?: defaultExecutableName()
    }
    return GeneralCommandLine(executable).withWorkDirectory(waveProject.basePath)
  }
}

private object BundledWaveAgape {
  @Synchronized
  fun resolve(): String? = runCatching {
    val platform = platformName()
    val architecture = architectureName()
    val executable = defaultExecutableName()
    val resourcePath = "server/$platform-$architecture/$executable"
    val input = WaveLspServerSupportProvider::class.java.classLoader
      .getResourceAsStream(resourcePath) ?: return null
    val pluginVersion = PluginManagerCore
      .getPlugin(PluginId.getId("dev.wavelang.wave"))
      ?.version ?: "development"
    val destination = Path.of(
      PathManager.getSystemPath(),
      "wave-agape",
      pluginVersion,
      "$platform-$architecture",
      executable,
    )
    Files.createDirectories(destination.parent)
    if (!Files.isRegularFile(destination) || Files.size(destination) == 0L) {
      input.use {
        Files.copy(it, destination, StandardCopyOption.REPLACE_EXISTING)
      }
    }
    else {
      input.close()
    }
    if (platform != "win32" && !destination.toFile().setExecutable(true)) {
      return null
    }
    destination.toString()
  }.getOrNull()
}

private fun platformName(): String = when {
  System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> "win32"
  System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> "darwin"
  else -> "linux"
}

private fun architectureName(): String = when (System.getProperty("os.arch").lowercase()) {
  "amd64", "x86_64" -> "x64"
  "aarch64", "arm64" -> "arm64"
  else -> System.getProperty("os.arch").lowercase()
}

private fun defaultExecutableName(): String =
  if (platformName() == "win32") "wave-agape.exe" else "wave-agape"
