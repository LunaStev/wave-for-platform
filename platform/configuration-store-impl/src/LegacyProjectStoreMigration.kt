package com.intellij.configurationStore

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.NioFiles
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** Copy once, leaving the original store intact. Never merge two independent stores. */
@Synchronized
internal fun migrateLegacyProjectStore(projectRoot: Path) {
  val source = projectRoot.resolve(".idea")
  val target = projectRoot.resolve(Project.DIRECTORY_STORE_FOLDER)
  if (Files.exists(target, NOFOLLOW_LINKS) || !Files.isDirectory(source, NOFOLLOW_LINKS)) return

  val staging = Files.createTempDirectory(projectRoot, ".wfp-import-")
  try {
    Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
      override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
        ProgressManager.checkCanceled()
        if (dir != source) Files.createDirectory(staging.resolve(source.relativize(dir)))
        return FileVisitResult.CONTINUE
      }

      override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        ProgressManager.checkCanceled()
        // Following or preserving a link could make future saves modify the original store.
        if (!attrs.isRegularFile) throw IOException(ConfigurationStoreBundle.message("project.settings.migration.non.regular.file", file))
        val copy = staging.resolve(source.relativize(file))
        Files.copy(file, copy)
        if (file.toString().endsWith(".xml") || file.toString().endsWith(".iml")) {
          val original = Files.readString(copy)
          val migrated = original
            .replace("\$PROJECT_DIR\$/.idea/", "\$PROJECT_DIR\$/${Project.DIRECTORY_STORE_FOLDER}/")
            .replace(source.toString().replace('\\', '/') + "/", target.toString().replace('\\', '/') + "/")
          if (migrated != original) Files.writeString(copy, migrated)
        }
        return FileVisitResult.CONTINUE
      }
    })
    // Same-filesystem rename; deliberately omit REPLACE_EXISTING (and ATOMIC_MOVE, which may replace a target).
    Files.move(staging, target)
  }
  finally {
    NioFiles.deleteRecursively(staging)
  }
}
