package dev.wavelang.intellij.wave.rust

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import dev.wavelang.intellij.wave.WaveBundle
import dev.wavelang.intellij.wave.cargo.CargoWorkspace
import dev.wavelang.intellij.wave.cargo.CargoWorkspaceService
import dev.wavelang.intellij.wave.cargo.nearestCargoManifest
import dev.wavelang.intellij.wave.cargo.projectRoot
import dev.wavelang.intellij.wave.toolchain.CompilerTool
import dev.wavelang.intellij.wave.toolchain.ToolExecutionOptions
import dev.wavelang.intellij.wave.toolchain.ToolProcessService
import dev.wavelang.intellij.wave.toolchain.ToolchainResolver
import dev.wavelang.intellij.wave.toolchain.ToolchainSettings
import java.nio.file.Path

internal fun rustEdition(model: CargoWorkspace, file: Path): String {
  val pkg = requireNotNull(model.packageFor(file)) { WaveBundle.message("rust.format.no.package") }
  val matching = pkg.targets.filter { file.startsWith(it.source.parent) }
  val mostSpecific = matching.maxOfOrNull { it.source.parent.nameCount }
  val editions = (if (mostSpecific == null) pkg.targets else matching.filter { it.source.parent.nameCount == mostSpecific })
    .map { it.edition }.distinct()
  return editions.singleOrNull() ?: throw IllegalArgumentException(WaveBundle.message("rust.format.ambiguous.edition"))
}

internal fun rustfmtArguments(file: Path, edition: String): List<String> =
  listOf("--emit", "stdout", "--edition", edition, "--config-path", file.parent.toString())

internal fun canApplyFormatting(originalStamp: Long, currentStamp: Long, success: Boolean, cancelled: Boolean): Boolean =
  success && !cancelled && originalStamp == currentStamp

class RustFormatAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null && e.getData(CommonDataKeys.VIRTUAL_FILE)?.extension == "rs" &&
      e.getData(CommonDataKeys.EDITOR) != null && !externalRustPluginActive()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    if (externalRustPluginActive()) return
    val document = editor.document
    if (!document.isWritable || document.textLength > 8 * 1024 * 1024) return
    val input = document.immutableCharSequence.toString()
    val stamp = document.modificationStamp
    val path = Path.of(file.path)
    object : Task.Backgroundable(project, WaveBundle.message("rust.format.title"), true) {
      private var formatted: String? = null
      override fun run(indicator: ProgressIndicator) {
        val manifest = nearestCargoManifest(path) ?: throw IllegalArgumentException(WaveBundle.message("cargo.no.manifest"))
        formatted = runBlockingMaybeCancellable {
          val workspaces = project.service<CargoWorkspaceService>()
          val workspace = workspaces.forFile(path) ?: workspaces.refresh(manifest, indicator)
          val rust = project.service<ToolchainSettings>().state.rust
          val command = ToolchainResolver(projectRoot(project)).rustCommand(CompilerTool.RUSTFMT, rust,
            rustfmtArguments(path, rustEdition(workspace, path)), indicator, manifest.parent)
          project.service<ToolProcessService>().execute(command,
            ToolExecutionOptions(captureLimit = 8 * 1024 * 1024, input = input), indicator).requireSuccess()
            .replace("\r\n", "\n")
        }
        indicator.checkCanceled()
      }

      override fun onSuccess() {
        val output = formatted ?: return
        if (project.isDisposed || editor.isDisposed || !file.isValid) return
        if (!canApplyFormatting(stamp, document.modificationStamp, true, false)) {
          Messages.showInfoMessage(project, WaveBundle.message("rust.format.changed"), WaveBundle.message("rust.format.title"))
          return
        }
        WriteCommandAction.runWriteCommandAction(project, WaveBundle.message("rust.format.title"), null, Runnable {
          if (document.isWritable && canApplyFormatting(stamp, document.modificationStamp, true, false)) document.setText(output)
        })
      }

      override fun onThrowable(error: Throwable) {
        if (!project.isDisposed) Messages.showErrorDialog(project, WaveBundle.message("rust.format.failed", error.message.orEmpty()),
                                                         WaveBundle.message("rust.format.title"))
      }
    }.queue()
  }
}
