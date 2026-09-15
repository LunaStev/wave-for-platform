package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.toPersistentList
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.productModules

internal val WAVE_EXCLUDED_PLUGIN_MODULES: Set<String> = setOf(
  "intellij.android.gradle.declarative.lang.ide",
  "intellij.android.gradle.dsl",
  "intellij.ant",
  "intellij.compose.ide.plugin",
  "intellij.eclipse",
  "intellij.featuresTrainer",
  "intellij.gradle.plugin",
  "intellij.gradle.java.plugin",
  "intellij.groovy",
  "intellij.groovy.scripting",
  "intellij.groovy.live.templates",
  "intellij.java.byteCodeViewer",
  "intellij.java.coverage",
  "intellij.java.decompiler",
  "intellij.java.guiForms.designer",
  "intellij.java.i18n",
  "intellij.javaFX.community",
  "intellij.junit",
  "intellij.maven.plugin",
  "intellij.repository.search",
  "intellij.testng",
  "intellij.vcs.hg",
  "intellij.vcs.svn",
)

internal val WAVE_BUNDLED_PLUGINS: PersistentList<String> = (
  IDEA_BUNDLED_PLUGINS.filterNot { it in WAVE_EXCLUDED_PLUGIN_MODULES } +
  listOf("intellij.wave")
).toPersistentList()

internal fun waveIdeBaseFragment(): ProductModulesContentSpec = productModules {
  deprecatedInclude("intellij.platform.resources", "META-INF/PlatformLangPlugin.xml")
  alias("com.intellij.modules.all")
  alias("com.intellij.modules.jsp.base")
  embeddedModule("intellij.platform.remoteServers.impl")
}
