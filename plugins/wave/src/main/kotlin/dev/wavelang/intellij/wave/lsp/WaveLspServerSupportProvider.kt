package dev.wavelang.intellij.wave.lsp

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.absolutePathString
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile

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

private val LOG = Logger.getInstance(WaveLspServerSupportProvider::class.java)

internal class WaveLspServerDescriptor(
  private val waveProject: Project,
) : ProjectWideLspServerDescriptor(waveProject, "Wave") {
  override fun isSupportedFile(file: VirtualFile): Boolean =
    file.extension.equals("wave", ignoreCase = true)

  override fun createCommandLine(): GeneralCommandLine {
    LOG.info("Discovering Wave language server executable for project ${waveProject.name}")
    val configured = System.getenv("WAVE_AGAPE_PATH")?.trim().orEmpty()
    
    val executablePath = resolveExecutable(configured)
    
    LOG.info("Selected Wave language server executable: $executablePath")
    // Keep assumptions about local versus remote execution explicit
    LOG.info("Assuming local execution for the Wave language server.")
    
    return GeneralCommandLine(executablePath).withWorkDirectory(waveProject.basePath)
  }
}

@VisibleForTesting
internal fun resolveExecutable(configuredPath: String): String {
  if (configuredPath.isNotEmpty()) {
    LOG.info("Using explicitly configured WAVE_AGAPE_PATH: $configuredPath")
    val path = Path.of(configuredPath)
    if (!path.isRegularFile()) {
      throw ExecutionException("Configured Wave language server not found at: $configuredPath. Please correct the WAVE_AGAPE_PATH environment variable.")
    }
    if (!path.isExecutable()) {
      throw ExecutionException("Configured Wave language server is not executable: $configuredPath. Please grant execute permissions.")
    }
    return configuredPath
  }

  LOG.info("WAVE_AGAPE_PATH is not set. Attempting to use bundled server.")
  return BundledWaveAgape.resolve() ?: run {
    val defaultName = defaultExecutableName()
    LOG.info("Bundled server unavailable. Falling back to local PATH using '$defaultName'.")
    defaultName
  }
}

internal object BundledWaveAgape {
  @Synchronized
  fun resolve(): String? {
    val platform = platformName()
    val architecture = architectureName()
    val executable = defaultExecutableName()
    val resourcePath = "server/$platform-$architecture/$executable"
    
    val input = WaveLspServerSupportProvider::class.java.classLoader
      .getResourceAsStream(resourcePath)
    
    if (input == null) {
      LOG.info("Bundled Wave language server not found in plugin resources for $platform-$architecture ($resourcePath). This optional bundle is missing.")
      return null
    }
    
    return try {
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
        LOG.info("Extracting bundled Wave language server to ${destination.absolutePathString()}")
        input.use {
          Files.copy(it, destination, StandardCopyOption.REPLACE_EXISTING)
        }
      } else {
        input.close()
      }
      
      if (platform != "win32" && !destination.toFile().setExecutable(true)) {
        throw ExecutionException("Failed to set execute permission on extracted bundled server at $destination")
      }
      
      destination.absolutePathString()
    } catch (e: Exception) {
      LOG.warn("Failed to extract or configure the bundled Wave language server", e)
      throw ExecutionException("Failed to extract bundled Wave language server: ${e.message}", e)
    }
  }
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

internal fun defaultExecutableName(): String =
  if (platformName() == "win32") "wave-agape.exe" else "wave-agape"
