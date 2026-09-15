// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

/** Provides shared content for Java editing. */
object CommunityProductFragments {
  fun javaIdeBaseFragment(): ProductModulesContentSpec = productModules {
    deprecatedInclude("intellij.platform.resources", "META-INF/PlatformLangPlugin.xml")
    alias("com.intellij.modules.all")
    embeddedModule("intellij.platform.remoteServers.impl")
  }
}
