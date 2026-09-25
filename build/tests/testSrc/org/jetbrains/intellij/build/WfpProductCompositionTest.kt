package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WfpProductCompositionTest {
  @Test
  fun `WfP retains compiler tools without the configuration script store override`() {
    val plugins = IdeaCommunityProperties(COMMUNITY_ROOT.communityRoot).productLayout.bundledPluginModules
    assertFalse("intellij.configurationScript" in plugins)
    assertTrue("intellij.wave" in plugins)
    assertTrue("intellij.vcs.git" in plugins)
    assertTrue("intellij.terminal" in plugins)
  }
}
