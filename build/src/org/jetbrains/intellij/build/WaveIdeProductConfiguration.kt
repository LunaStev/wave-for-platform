package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.productModules

internal val WAVE_BUNDLED_PLUGINS: PersistentList<String> = (
  IDEA_BUNDLED_PLUGINS.filterNot { it == "intellij.configurationScript" } +
  listOf("intellij.wave")
).toPersistentList()

internal fun waveIdeBaseFragment(): ProductModulesContentSpec = productModules {
  deprecatedInclude("intellij.platform.resources", "META-INF/PlatformLangPlugin.xml")
  alias("com.intellij.modules.all")
  embeddedModule("intellij.platform.remoteServers.impl")
}
