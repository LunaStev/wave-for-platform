package com.intellij.configurationStore

import com.intellij.openapi.project.Project
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class LegacyProjectStoreMigrationTest {
  @TempDir
  lateinit var root: Path

  @Test
  fun `copies settings once and rewrites module references without changing the source`() {
    val legacy = Files.createDirectory(root.resolve(".idea"))
    val original = "<module filepath=\"\$PROJECT_DIR\$/.idea/compiler.iml\"/>"
    Files.writeString(legacy.resolve("modules.xml"), original)
    Files.writeString(legacy.resolve("workspace.xml"), "workspace state")
    Files.writeString(legacy.resolve("compiler.iml"), "<module/>")
    Files.createDirectory(legacy.resolve("runConfigurations"))
    Files.writeString(legacy.resolve("runConfigurations/check.xml"), "<configuration/>")

    migrateLegacyProjectStore(root)
    val store = root.resolve(Project.DIRECTORY_STORE_FOLDER)
    assertEquals(".wfp", Project.DIRECTORY_STORE_FOLDER)
    assertEquals(original, Files.readString(legacy.resolve("modules.xml")))
    assertEquals(original.replace("/.idea/", "/.wfp/"), Files.readString(store.resolve("modules.xml")))
    assertEquals("workspace state", Files.readString(store.resolve("workspace.xml")))
    assertTrue(Files.exists(store.resolve("runConfigurations/check.xml")))
    Files.writeString(store.resolve("workspace.xml"), "new WfP state")
    migrateLegacyProjectStore(root)
    assertEquals("new WfP state", Files.readString(store.resolve("workspace.xml")))
    assertEquals("workspace state", Files.readString(legacy.resolve("workspace.xml")))
  }

  @Test
  fun `does not create a store for a plain directory`() {
    migrateLegacyProjectStore(root)
    assertFalse(Files.exists(root.resolve(".wfp")))
  }

  @Test
  fun `an existing WfP store always wins including an empty one`() {
    Files.createDirectory(root.resolve(".wfp"))
    val legacy = Files.createDirectory(root.resolve(".idea"))
    Files.writeString(legacy.resolve("workspace.xml"), "old")
    migrateLegacyProjectStore(root)
    assertFalse(Files.exists(root.resolve(".wfp/workspace.xml")))
  }

  @Test
  fun `unsafe symlink fails without publishing a partial store`() {
    assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"))
    val legacy = Files.createDirectory(root.resolve(".idea"))
    Files.writeString(legacy.resolve("misc.xml"), "<project/>")
    Files.createSymbolicLink(legacy.resolve("linked"), root)
    assertThrows(IOException::class.java) { migrateLegacyProjectStore(root) }
    assertFalse(Files.exists(root.resolve(".wfp")))
    Files.list(root).use { children -> assertFalse(children.anyMatch { it.fileName.toString().startsWith(".wfp-import-") }) }
    assertEquals("<project/>", Files.readString(legacy.resolve("misc.xml")))
  }
}
