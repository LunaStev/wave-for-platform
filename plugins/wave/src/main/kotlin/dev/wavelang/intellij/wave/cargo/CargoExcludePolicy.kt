package dev.wavelang.intellij.wave.cargo

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.LocalFileSystem

class CargoExcludePolicy(private val project: Project) : DirectoryIndexExcludePolicy {
  override fun getExcludeUrlsForProject(): Array<String> = buildSet {
    val root = project.basePath
    if (root != null && LocalFileSystem.getInstance().findFileByPath(root)?.findChild("Cargo.toml") != null) {
      add(VfsUtilCore.pathToUrl("$root/target"))
    }
    for (workspace in project.service<CargoWorkspaceService>().snapshots()) {
      add(VfsUtilCore.pathToUrl(workspace.targetDirectory.toString()))
    }
  }.toTypedArray()
}
