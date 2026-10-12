package dev.wavelang.intellij.wave.rust

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.BaseProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.spawnProcess
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientDescriptor
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspClientManagerListener
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.openapi.components.Service
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspFormattingDisabled
import com.intellij.platform.lsp.api.lsWidget.LspClientWidgetItem
import com.intellij.util.io.BaseOutputReader
import dev.wavelang.intellij.wave.WaveBundle
import dev.wavelang.intellij.wave.cargo.CargoWorkspaceService
import dev.wavelang.intellij.wave.cargo.projectRoot
import dev.wavelang.intellij.wave.toolchain.CompilerTool
import dev.wavelang.intellij.wave.toolchain.ToolProcessService
import dev.wavelang.intellij.wave.toolchain.ToolchainConfigurable
import dev.wavelang.intellij.wave.toolchain.ToolchainResolver
import dev.wavelang.intellij.wave.toolchain.ToolchainSettings
import java.nio.file.Path
import org.eclipse.lsp4j.ConfigurationItem

class RustLspIntegrationProvider : LspIntegrationProvider {
  override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
    if (file.extension != "rs" || !project.service<ToolchainSettings>().state.rust.languageServiceEnabled) return
    project.service<RustLanguageOwnership>()
    if (externalRustPluginActive()) return
    // An existing external LSP client owns its Rust documents. Never create a second diagnostics/completion owner.
    val external = LspIntegrationProvider.getAllExtensions().filter { it !is RustLspIntegrationProvider }.any { provider ->
      LspClientManager.getInstance(project).getClients(provider.javaClass).any {
        it.state !in setOf(LspServerState.ShutdownNormally, LspServerState.ShutdownUnexpectedly) && it.descriptor.isSupportedFile(file)
      }
    }
    if (external) return
    val path = Path.of(file.path)
    val workspaces = project.service<CargoWorkspaceService>()
    val workspace = workspaces.forFile(path)
    if (workspace == null) {
      // Never run Cargo while fileOpened holds the read lock. Request its offline model in the project scope.
      workspaces.requestRustWorkspace(path)
      return
    }
    val manifest = workspace.root.resolve("Cargo.toml")
    val root = LocalFileSystem.getInstance().findFileByNioFile(manifest.parent) ?: return
    clientStarter.ensureClientStarted(RustLspDescriptor(project, root, manifest))
  }

  override fun createWidgetItem(lspClient: LspClient, currentFile: VirtualFile?): LspClientWidgetItem =
    LspClientWidgetItem(lspClient, currentFile, AllIcons.RunConfigurations.Application, ToolchainConfigurable::class.java)
}

internal fun externalRustPluginActive(): Boolean = listOf("org.rust", "com.jetbrains.rust", "com.jetbrains.rustrover").any {
  PluginManagerCore.getPlugin(PluginId.getId(it))?.isEnabled == true
}

/** An external provider that starts later also takes ownership; stop the built-in adapter rather than duplicate editor diagnostics. */
@Service(Service.Level.PROJECT)
internal class RustLanguageOwnership(private val project: Project) : Disposable {
  init {
    LspClientManager.getInstance(project).addListener(object : LspClientManagerListener {
      override fun fileOpened(lspClient: LspClient, file: VirtualFile) {
        if (lspClient.providerClass == RustLspIntegrationProvider::class.java || file.extension != "rs") return
        ApplicationManager.getApplication().invokeLater {
          if (!project.isDisposed) LspClientManager.getInstance(project).stopClients(RustLspIntegrationProvider::class.java)
        }
      }
    }, this, sendEventsForExistingClients = true)
  }

  override fun dispose() {}
}

internal fun rustAnalyzerOptions(manifest: Path, environment: Map<String, String> = emptyMap()): Map<String, Any> = mapOf(
  "linkedProjects" to listOf(manifest.toString()),
  "checkOnSave" to false,
  "cargo" to mapOf("buildScripts" to mapOf("enable" to false), "noDeps" to true,
                    "extraArgs" to listOf("--offline", "--locked"), "metadataExtraArgs" to listOf("--offline", "--locked"),
                    "extraEnv" to environment),
  "procMacro" to mapOf("enable" to false),
  "cachePriming" to mapOf("enable" to false),
)

internal class RustLspDescriptor(project: Project, root: VirtualFile, private val manifest: Path) :
  LspClientDescriptor(project, WaveBundle.message("rust.lsp.title"), root) {
  @Volatile private var toolEnvironment: Map<String, String> = emptyMap()
  override fun isSupportedFile(file: VirtualFile): Boolean = file.extension == "rs"
  override fun getLanguageId(file: VirtualFile): String = "rust"
  override fun createInitializationOptions(): Any = rustAnalyzerOptions(manifest, toolEnvironment)
  override fun getWorkspaceConfiguration(item: ConfigurationItem): Any? =
    if (item.section == "rust-analyzer" || item.section.isNullOrEmpty()) rustAnalyzerOptions(manifest, toolEnvironment)
    else super.getWorkspaceConfiguration(item)
  override val lspCustomization: LspCustomization = object : LspCustomization() {
    // One explicit formatter owns Rust edits. Built-in LSP formatting cannot also run on save.
    override val formattingCustomizer = LspFormattingDisabled
  }

  override fun startServerProcess(): BaseProcessHandler<*> {
    try {
      val rust = project.service<ToolchainSettings>().state.rust
      val resolver = ToolchainResolver(projectRoot(project))
      val indicator = EmptyProgressIndicator()
      val analyzer = resolver.rustCommand(CompilerTool.RUST_ANALYZER, rust, emptyList(), indicator, manifest.parent)
      val cargo = resolver.rustCommand(CompilerTool.CARGO, rust, emptyList(), indicator, manifest.parent)
      val rustc = resolver.rustCommand(CompilerTool.RUSTC, rust, emptyList(), indicator, manifest.parent)
      val environment = analyzer.environment + mapOf("CARGO" to cargo.executable.toString(), "RUSTC" to rustc.executable.toString(),
                                                     "CARGO_NET_OFFLINE" to "true")
      val sharedKeys = setOf("CARGO", "RUSTC", "CARGO_NET_OFFLINE", "RUSTUP_AUTO_INSTALL", "RUSTUP_TOOLCHAIN")
      toolEnvironment = environment.filterKeys { it in sharedKeys }
      val process = runBlockingMaybeCancellable {
        localEel.exec.spawnProcess(analyzer.executable.asEelPath()).args(analyzer.arguments)
          .workingDirectory(analyzer.workingDirectory.asEelPath()).env(environment)
          .scope(project.service<ToolProcessService>().scope).eelIt().convertToJavaProcess()
      }
      return object : OSProcessHandler(process, analyzer.executable.toString(), Charsets.UTF_8) {
        override fun readerOptions(): BaseOutputReader.Options = object : BaseOutputReader.Options() {
          override fun splitToLines(): Boolean = false
        }
      }
    }
    catch (e: ProcessCanceledException) { throw e }
    catch (e: Exception) { throw ExecutionException(WaveBundle.message("rust.lsp.setup", e.message.orEmpty()), e) }
  }
}
