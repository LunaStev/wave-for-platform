package dev.wavelang.intellij.wave.cargo

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import dev.wavelang.intellij.wave.WaveBundle
import dev.wavelang.intellij.wave.toolchain.CompilerTool
import dev.wavelang.intellij.wave.toolchain.ToolExecutionOptions
import dev.wavelang.intellij.wave.toolchain.ToolProcessService
import dev.wavelang.intellij.wave.toolchain.ToolchainResolver
import dev.wavelang.intellij.wave.toolchain.ToolchainSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal data class CargoTarget(val name: String, val kinds: List<String>, val source: Path, val edition: String)
internal data class CargoPackage(val id: String, val name: String, val manifest: Path, val targets: List<CargoTarget>)
internal data class CargoWorkspace(val root: Path, val targetDirectory: Path, val packages: List<CargoPackage>) {
  fun packageFor(file: Path): CargoPackage? = packages.filter { file.startsWith(it.manifest.parent) }
    .maxByOrNull { it.manifest.parent.nameCount }
}

internal fun parseCargoMetadata(text: String): CargoWorkspace {
  val json = JsonParser.parseString(text).asJsonObject
  require(json.get("version")?.asInt == 1) { WaveBundle.message("cargo.metadata.version") }
  val members = json.getAsJsonArray("workspace_members").map { it.asString }.toSet()
  val packages = json.getAsJsonArray("packages").map { it.asJsonObject }.filter { it.string("id") in members }.map { pkg ->
    CargoPackage(pkg.string("id"), pkg.string("name"), Path.of(pkg.string("manifest_path")),
                 pkg.getAsJsonArray("targets").map { entry ->
                   val target = entry.asJsonObject
                   CargoTarget(target.string("name"), target.getAsJsonArray("kind").map { it.asString },
                               Path.of(target.string("src_path")), target.string("edition"))
                 })
  }
  return CargoWorkspace(Path.of(json.string("workspace_root")), Path.of(json.string("target_directory")), packages)
}

internal fun JsonObject.string(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.asString
  ?: throw IllegalArgumentException(WaveBundle.message("cargo.metadata.field", key))

internal fun nearestCargoManifest(start: Path): Path? {
  var directory: Path? = if (Files.isDirectory(start)) start else start.parent
  while (directory != null) {
    val manifest = directory.resolve("Cargo.toml")
    if (Files.isRegularFile(manifest)) return manifest
    directory = directory.parent
  }
  return null
}

/** Bounded discovery for explicitly selected directories; output, VCS and IDE state are never traversed. */
internal fun discoverCargoManifests(root: Path): List<Path> {
  val manifests = ArrayList<Path>()
  var visited = 0
  Files.walkFileTree(root, emptySet(), 6, object : SimpleFileVisitor<Path>() {
    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
      if (++visited > 5000) return FileVisitResult.TERMINATE
      return if (dir != root && dir.fileName.toString() in setOf("target", ".git", ".idea", ".wfp")) {
        FileVisitResult.SKIP_SUBTREE
      }
      else FileVisitResult.CONTINUE
    }

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
      if (++visited > 5000) return FileVisitResult.TERMINATE
      if (file.fileName.toString() == "Cargo.toml" && attrs.isRegularFile) manifests.add(file)
      return FileVisitResult.CONTINUE
    }

    override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult = FileVisitResult.CONTINUE
  })
  return manifests.sortedBy { it.toString() }
}

/** Only offline metadata is read: on semantic editing, explicit refresh/run, or changes to an already loaded model. */
@Service(Service.Level.PROJECT)
internal class CargoWorkspaceService(private val project: Project, private val scope: CoroutineScope) {
  private val workspaces = ConcurrentHashMap<Path, CargoWorkspace>()
  private val generation = AtomicLong()
  private val semanticRequests = ConcurrentHashMap.newKeySet<Path>()
  @Volatile var lastError: String? = null
    private set

  init {
    project.messageBus.connect(scope).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        if (events.none { affectsCargoModel(it.path) }) return
        generation.incrementAndGet()
        val manifests = (workspaces.keys + semanticRequests).distinct()
        semanticRequests.clear()
        workspaces.clear()
        notifyRootsChanged()
        if (manifests.isNotEmpty()) scope.launch(Dispatchers.IO) {
          for (manifest in manifests) {
            try {
              refresh(manifest, com.intellij.openapi.progress.EmptyProgressIndicator())
            }
            catch (e: kotlinx.coroutines.CancellationException) {
              throw e
            }
            catch (e: Exception) {
              lastError = e.message
            }
          }
          if (!project.isDisposed) LspClientManager.getInstance(project)
            .stopAndRestartClientsIfNeeded(dev.wavelang.intellij.wave.rust.RustLspIntegrationProvider::class.java)
        }
      }
    })
  }

  fun snapshots(): List<CargoWorkspace> = workspaces.values.distinctBy { it.root }.sortedBy { it.root.toString() }
  fun forFile(file: Path): CargoWorkspace? = snapshots().filter { it.packageFor(file) != null }.maxByOrNull { it.root.nameCount }

  fun requestRustWorkspace(file: Path) {
    val manifest = nearestCargoManifest(file) ?: return
    if (!semanticRequests.add(manifest)) return
    scope.launch(Dispatchers.IO) {
      try {
        refresh(manifest, com.intellij.openapi.progress.EmptyProgressIndicator())
        if (!project.isDisposed) LspClientManager.getInstance(project)
          .startClientsIfNeeded(dev.wavelang.intellij.wave.rust.RustLspIntegrationProvider::class.java)
      }
      catch (e: kotlinx.coroutines.CancellationException) { throw e }
      catch (e: Exception) {
        lastError = e.message
        if (!project.isDisposed) NotificationGroupManager.getInstance().getNotificationGroup("WfP Rust")
          .createNotification(WaveBundle.message("rust.lsp.setup", e.message.orEmpty()), NotificationType.WARNING).notify(project)
      }
    }
  }

  suspend fun refresh(manifest: Path, indicator: ProgressIndicator): CargoWorkspace {
    val version = generation.get()
    val selectedManifest = manifest.toAbsolutePath().normalize()
    val root = selectedManifest.parent
    val rust = project.service<ToolchainSettings>().state.rust
    val resolver = ToolchainResolver(projectRoot(project))
    val rustc = resolver.rustCommand(CompilerTool.RUSTC, rust, emptyList(), indicator, root)
    val command = resolver.rustCommand(CompilerTool.CARGO, rust,
      listOf("metadata", "--format-version", "1", "--no-deps", "--offline", "--locked", "--manifest-path", selectedManifest.toString()),
      indicator, root)
      .let { it.copy(environment = it.environment + ("RUSTC" to rustc.executable.toString())) }
    val result = project.service<ToolProcessService>().execute(command, ToolExecutionOptions(captureLimit = 8 * 1024 * 1024), indicator)
    val model = parseCargoMetadata(result.requireSuccess())
    if (version == generation.get()) {
      workspaces[selectedManifest] = model
      notifyRootsChanged()
      if (!project.isDisposed && !project.isDefault) LspClientManager.getInstance(project)
        .startClientsIfNeeded(dev.wavelang.intellij.wave.rust.RustLspIntegrationProvider::class.java)
    }
    lastError = null
    return model
  }

  fun invalidate() {
    generation.incrementAndGet()
    workspaces.clear()
    semanticRequests.clear()
    notifyRootsChanged()
  }

  private fun notifyRootsChanged() {
    ApplicationManager.getApplication().invokeLater {
      if (!project.isDisposed && !project.isDefault) WriteAction.run<RuntimeException> {
        ProjectRootManagerEx.getInstanceEx(project).makeRootsChange({}, RootsChangeRescanningInfo.RESCAN_DEPENDENCIES_IF_NEEDED)
      }
    }
  }
}

internal fun affectsCargoModel(path: String): Boolean = path.substringAfterLast('/') in
  setOf("Cargo.toml", "Cargo.lock", "rust-toolchain", "rust-toolchain.toml") ||
  path.endsWith("/.cargo/config") || path.endsWith("/.cargo/config.toml")

internal fun projectRoot(project: Project): Path = Path.of(
  project.basePath ?: throw IllegalArgumentException(WaveBundle.message("toolchains.no.project.root")),
)
