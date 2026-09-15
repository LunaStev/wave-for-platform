package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
@SystemProperty(propertyKey = "idea.default.disabled.plugins.id", propertyValue = "test.ai")
class DefaultDisabledPluginsTest {
  @Test
  fun `first startup persists the disabled defaults`(@TempDir directory: Path) {
    val file = directory.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME)
    assertEquals(setOf(PluginId.getId("test.ai")), DisabledPluginsState.loadDisabledPlugins(file))
    assertEquals(setOf("test.ai"), PluginStringSetFile.read(file))
  }

  @Test
  fun `enabling a default plugin survives the next startup`(@TempDir directory: Path) {
    val file = directory.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME)
    DisabledPluginsState.loadDisabledPlugins(file)
    writePluginStringSet(file, emptySet())
    assertTrue(DisabledPluginsState.loadDisabledPlugins(file).isEmpty())
    assertTrue(Files.exists(file))
  }

  @Test
  fun `existing user choices are preserved`(@TempDir directory: Path) {
    val file = directory.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME)
    writePluginStringSet(file, setOf("test.other"))
    assertEquals(setOf(PluginId.getId("test.other")), DisabledPluginsState.loadDisabledPlugins(file))
    assertEquals(setOf("test.other"), PluginStringSetFile.read(file))
  }
}
