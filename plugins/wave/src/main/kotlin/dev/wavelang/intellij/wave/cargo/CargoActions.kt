package dev.wavelang.intellij.wave.cargo

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import dev.wavelang.intellij.wave.WaveBundle
import java.nio.file.Path

class CargoRefreshAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  override fun update(e: AnActionEvent) { e.presentation.isEnabledAndVisible = e.project != null }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val selected = e.getData(CommonDataKeys.VIRTUAL_FILE)?.path?.let { Path.of(it) }
    object : Task.Backgroundable(project, WaveBundle.message("cargo.refresh"), true) {
      private var manifests = emptyList<Path>()
      override fun run(indicator: ProgressIndicator) {
        val root = projectRoot(project)
        val nearest = nearestCargoManifest(selected ?: root)
        manifests = if (nearest != null) listOf(nearest) else discoverCargoManifests(root)
      }

      override fun onSuccess() {
        if (project.isDisposed) return
        if (manifests.isEmpty()) {
          Messages.showInfoMessage(project, WaveBundle.message("cargo.no.manifest"), WaveBundle.message("cargo.refresh"))
          return
        }
        fun load(manifest: Path) {
          object : Task.Backgroundable(project, WaveBundle.message("cargo.refresh"), true) {
            private var model: CargoWorkspace? = null
            override fun run(indicator: ProgressIndicator) {
              model = runBlockingMaybeCancellable { project.service<CargoWorkspaceService>().refresh(manifest, indicator) }
            }

            override fun onSuccess() {
              if (!project.isDisposed) model?.let { showCargoTargets(project, it) }
            }

            override fun onThrowable(error: Throwable) {
              if (!project.isDisposed) Messages.showErrorDialog(
                project, WaveBundle.message("cargo.refresh.failed", error.message.orEmpty()), WaveBundle.message("cargo.refresh"),
              )
            }
          }.queue()
        }
        if (manifests.size == 1) load(manifests.single())
        else JBPopupFactory.getInstance().createPopupChooserBuilder(manifests)
          .setTitle(WaveBundle.message("cargo.select.manifest")).setItemChosenCallback { load(it) }
          .createPopup().showCenteredInCurrentWindow(project)
      }
    }.queue()
  }
}

class CargoTargetsAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  override fun update(e: AnActionEvent) { e.presentation.isEnabledAndVisible = e.project != null }
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val models = project.service<CargoWorkspaceService>().snapshots()
    if (models.isEmpty()) Messages.showInfoMessage(project, WaveBundle.message("cargo.refresh.first"), WaveBundle.message("cargo.targets"))
    else showCargoTargets(project, models)
  }
}

private fun showCargoTargets(project: Project, model: CargoWorkspace) = showCargoTargets(project, listOf(model))

private fun showCargoTargets(project: Project, models: List<CargoWorkspace>) {
  data class Choice(val pkg: String, val target: CargoTarget) {
    override fun toString(): String = "$pkg / ${target.kinds.joinToString(",")} / ${target.name} — ${target.source}"
  }
  val choices = models.flatMap { it.packages }.flatMap { pkg -> pkg.targets.map { Choice(pkg.name, it) } }
  JBPopupFactory.getInstance().createPopupChooserBuilder(choices).setTitle(WaveBundle.message("cargo.targets"))
    .setItemChosenCallback { choice ->
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(choice.target.source)?.let {
        OpenFileDescriptor(project, it).navigate(true)
      }
    }.createPopup().showCenteredInCurrentWindow(project)
}
