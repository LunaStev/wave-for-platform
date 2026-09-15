// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.jetbrains.intellij.build.impl.PluginLayout

object JavaPluginLayout {
  const val MAIN_MODULE_NAME = "intellij.java.plugin"

  fun javaPlugin(addition: ((PluginLayout.PluginLayoutSpec) -> Unit)? = null): PluginLayout {
    return PluginLayout.plugin(mainModuleName = MAIN_MODULE_NAME, auto = true) { spec ->
      spec.directoryName = "java"
      spec.mainJarName = "java-impl.jar"

      spec.withModule("intellij.platform.jps.build.launcher", "jps-launcher.jar")

      spec.withProjectLibrary("netty-codec-protobuf", "netty-codec-protobuf.jar")

      spec.withProjectLibrary("Eclipse", "ecj")
      spec.withProjectLibrary("jps-javac-extension")
      spec.withProjectLibrary("kotlin-metadata")

      spec.withResourceArchive("../jdkAnnotations", "lib/resources/jdkAnnotations.jar")

      addition?.invoke(spec)
    }
  }
}
