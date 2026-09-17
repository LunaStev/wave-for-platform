// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LiftReturnOrAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.intellij.build

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.BuildDependenciesExtractOptions
import org.jetbrains.intellij.build.impl.LibraryPackMode
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.PluginLayout.Companion.plugin
import org.jetbrains.intellij.build.impl.PluginLayout.Companion.pluginAuto
import org.jetbrains.intellij.build.impl.PluginLayout.Companion.pluginAutoWithCustomDirName
import org.jetbrains.intellij.build.impl.PluginVersionEvaluatorResult
import org.jetbrains.intellij.build.impl.SupportedDistribution
import org.jetbrains.intellij.build.impl.patchOsSpecificPluginXml
import org.jetbrains.intellij.build.kotlin.CommunityKotlinPluginBuilder
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object CommunityRepositoryModules {
  /**
   * Specifies non-trivial layout for all plugins that sources are located in 'community' and 'contrib' repositories
   */
  val COMMUNITY_REPOSITORY_PLUGINS: PersistentList<PluginLayout> = persistentListOf(
    plugin("intellij.laf.macos") { spec ->
      spec.bundlingRestrictions.supportedOs = persistentListOf(OsFamily.MACOS)
    },
    plugin("intellij.webp") { spec ->
      spec.withPlatformBin(OsFamily.WINDOWS, JvmArchitecture.x64, WindowsLibcImpl.DEFAULT, "plugins/webp/lib/libwebp/win", "lib/libwebp/win")
      spec.withPlatformBin(OsFamily.MACOS, JvmArchitecture.x64, MacLibcImpl.DEFAULT, "plugins/webp/lib/libwebp/mac", "lib/libwebp/mac")
      spec.withPlatformBin(OsFamily.MACOS, JvmArchitecture.aarch64, MacLibcImpl.DEFAULT, "plugins/webp/lib/libwebp/mac", "lib/libwebp/mac")
      spec.withPlatformBin(OsFamily.LINUX, JvmArchitecture.x64, LinuxLibcImpl.GLIBC, "plugins/webp/lib/libwebp/linux", "lib/libwebp/linux")
    },
    plugin("intellij.webp") { spec ->
      spec.bundlingRestrictions.marketplace = true
      spec.withResource("lib/libwebp/linux", "lib/libwebp/linux")
      spec.withResource("lib/libwebp/mac", "lib/libwebp/mac")
      spec.withResource("lib/libwebp/win", "lib/libwebp/win")
    },
    plugin("intellij.laf.win10") { spec ->
      spec.bundlingRestrictions.supportedOs = persistentListOf(OsFamily.WINDOWS)
    },
    CommunityKotlinPluginBuilder.kotlinPlugin(),
    pluginAuto("intellij.grazie") { spec ->
      spec.withModuleLibrary(
        libraryName = "org.jetbrains.intellij.deps.languagetool:languagetool-core",
        moduleName = "intellij.grazie.core",
        relativeOutputPath = "org.jetbrains.intellij.deps.languagetool-languagetool-core.jar",
      )
      spec.withModuleLibrary(
        libraryName = "org.jetbrains.intellij.deps.languagetool:language-en",
        moduleName = "intellij.grazie.core",
        relativeOutputPath = "org.jetbrains.intellij.deps.languagetool-language-en.jar",
      )
    },
    pluginAuto(listOf("intellij.vcs.git")) { spec ->
      spec.withModule("intellij.vcs.git.rt", "git4idea-rt.jar")
    },
    pluginAuto(listOf("intellij.xpath")) { spec ->
      spec.withModule("intellij.xpath.rt", "rt/xslt-rt.jar")
    },
    pluginAutoWithCustomDirName("intellij.tasks.core") { spec ->
      spec.directoryName = "tasks"
      spec.withModule("intellij.tasks")
      spec.withModule("intellij.tasks.compatibility")
    },
    pluginAuto(listOf("intellij.devkit")) { spec ->
      spec.withModule("intellij.devkit.jps")

      spec.bundlingRestrictions.includeInDistribution = PluginDistribution.NOT_FOR_PUBLIC_BUILDS
    },
    pluginAuto("intellij.terminal") { spec ->
      spec.withModule("intellij.terminal.completion")
      spec.withResource("resources/shell-integrations", "shell-integrations")
    },
    pluginAuto(listOf("intellij.textmate.plugin")) { spec ->
      spec.withResourceFromModule("intellij.textmate", "lib/bundles", "lib/bundles")
    },
    pluginAuto(listOf("intellij.completionMlRankingModels")) { spec ->
      spec.bundlingRestrictions.includeInDistribution = PluginDistribution.NOT_FOR_RELEASE
    },
    pluginAuto(listOf("intellij.statsCollector")) { spec ->
      spec.bundlingRestrictions.includeInDistribution = PluginDistribution.NOT_FOR_RELEASE
    },
    pluginAuto(listOf("intellij.findUsagesMl")) { spec ->
      spec.bundlingRestrictions.includeInDistribution = PluginDistribution.NOT_FOR_RELEASE
    },
    pluginAuto(listOf("intellij.performanceTesting.ui")),
    pluginAuto(listOf("intellij.vcs.github")),
    pluginAuto(listOf("intellij.vcs.gitlab")),
    *allJcefPlugins()
  )

  val CONTRIB_REPOSITORY_PLUGINS: List<PluginLayout> = java.util.List.of(
    pluginAuto("intellij.errorProne") { spec ->
      spec.withModule("intellij.errorProne.jps", "jps/errorProne-jps.jar")
    },
    pluginAuto("intellij.serial.monitor") { spec ->
      // jSerialComm java JAR - Remember to update the binary dependency when updating to a new version!
      spec.withProjectLibrary("jetbrains.intellij.deps.jSerialComm", LibraryPackMode.STANDALONE_SEPARATE)

      // jSerialComm native library
      spec.withGeneratedResources { targetDir, context ->
        val uri = URI.create("https://packages.jetbrains.team/files/p/ij/intellij-build-dependencies/jSerialComm/9a7813435b79aa2e23c7f2a78f1b66b48c0504c4/jSerialComm.zip")
        val downloaded = BuildDependenciesDownloader.downloadFileToCacheLocation(context.paths.communityHomeDirRoot, uri)
        BuildDependenciesDownloader.extractFile(downloaded, targetDir.resolve("bin"), context.paths.communityHomeDirRoot)
      }
    },
  )

  fun allJcefPlugins(): Array<PluginLayout> {
    val supportedOsArch = listOf(
      SupportedDistribution(os = OsFamily.MACOS, arch = JvmArchitecture.x64, MacLibcImpl.DEFAULT),
      SupportedDistribution(os = OsFamily.MACOS, arch = JvmArchitecture.aarch64, MacLibcImpl.DEFAULT),
      SupportedDistribution(os = OsFamily.WINDOWS, arch = JvmArchitecture.x64, WindowsLibcImpl.DEFAULT),
      SupportedDistribution(os = OsFamily.WINDOWS, arch = JvmArchitecture.aarch64, WindowsLibcImpl.DEFAULT),
      SupportedDistribution(os = OsFamily.LINUX, arch = JvmArchitecture.x64, LinuxLibcImpl.GLIBC),
      SupportedDistribution(os = OsFamily.LINUX, arch = JvmArchitecture.aarch64, LinuxLibcImpl.GLIBC),
    )

    val allLayouts = ArrayList(supportedOsArch.map { (os, arch, _) -> jcefPlugin(os, arch) })
    allLayouts += jcefCrossPlatformEmpty()
    return allLayouts.toTypedArray()
  }

  private fun jcefCrossPlatformEmpty(): PluginLayout {
    return plugin("intellij.jcef.plugin") { // cross-platform distribution comes without JCEF binaries
      it.bundlingRestrictions.includeInDistribution = PluginDistribution.CROSS_PLATFORM_DIST_ONLY
    }
  }

  fun jcefPlugin(os: OsFamily, arch: JvmArchitecture): PluginLayout {
    return plugin("intellij.jcef.plugin") { spec ->
      spec.bundlingRestrictions.supportedOs = persistentListOf(os)
      spec.bundlingRestrictions.supportedArch = persistentListOf(arch)

      fun archSuffix(arch: JvmArchitecture): String = when (arch) {
        JvmArchitecture.x64 -> "x64"
        JvmArchitecture.aarch64 -> "aarch64"
      }

      fun jcefArchiveName(os: OsFamily, arch: JvmArchitecture, build: String): String =
        "jcef-${os.jbrArchiveSuffix}-${archSuffix(arch)}-${build}.tar.gz"

      fun downloadUrlFor(os: OsFamily, arch: JvmArchitecture, build: String): String =
        "https://cache-redirector.jetbrains.com/intellij-jbr/${jcefArchiveName(os, arch, build)}"

      patchOsSpecificPluginXml(spec, os, arch)

      spec.withCustomVersion { _, ideBuildNumber, _ ->
        // be careful, Marketplace expects linux/macos/windows for os and x86_64/x86/arm64/arm32 for arch
        val pluginVersion = "$ideBuildNumber-${os.osId}-${arch.marketplaceName}"
        PluginVersionEvaluatorResult(pluginVersion)
      }

      spec.withGeneratedResources { targetDir, context ->
        val communityRoot = context.paths.communityHomeDirRoot
        val properties = BuildDependenciesDownloader.getDependencyProperties(communityRoot)
        val jcefBuildNumber = properties.property("jcefBuild")

        val archivePath = downloadFileToCacheLocation(downloadUrlFor(os, arch, jcefBuildNumber), communityRoot)
        val subDir = targetDir.resolve("jcef-tmp") // to not clean up root plugin directory on BuildDependenciesDownloader.extractFile
        Files.createDirectories(subDir)

        BuildDependenciesDownloader.extractFile(archivePath, subDir, communityRoot, BuildDependenciesExtractOptions.STRIP_ROOT)

        // Unix ZIP does not have root `jcef` directory
        val jcefOutputDir = if (Files.exists(subDir.resolve("jcef"))) subDir.resolve("jcef") else subDir
        Files.move(jcefOutputDir, targetDir.resolve("jcef"), StandardCopyOption.REPLACE_EXISTING)
        Files.deleteIfExists(subDir)
      }

      spec.enableSymlinksAndExecutableResources()
    }
  }

}
