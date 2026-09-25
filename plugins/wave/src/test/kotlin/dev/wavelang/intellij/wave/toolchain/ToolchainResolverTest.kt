package dev.wavelang.intellij.wave.toolchain

import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.util.xmlb.XmlSerializer
import dev.wavelang.intellij.wave.WaveBundle
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

class ToolchainResolverTest {
  @TempDir
  lateinit var root: Path

  private val indicator = EmptyProgressIndicator()

  private fun tool(name: String, contents: String = "#!/bin/sh\nexit 0\n"): Path {
    val path = root.resolve(name)
    Files.createDirectories(path.parent)
    Files.writeString(path, contents)
    if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
      Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"))
    }
    return path
  }

  @Test
  fun `explicit paths take precedence over rustup and PATH`() {
    val selected = tool("도구 디렉터리/custom cargo")
    tool("bin/rustup")
    tool("bin/cargo")
    val resolver = ToolchainResolver(root, mapOf("PATH" to root.resolve("bin").toString()), ToolProbe { _, _ ->
      error("Explicit selection must not call rustup")
    })
    val command = resolver.rustCommand(CompilerTool.CARGO, RustToolchain(cargo = selected.toString()), listOf("--version"), indicator)
    assertEquals(selected, command.executable)
    assertEquals(listOf("--version"), command.arguments)
  }

  @Test
  fun `rustup resolves installed tools in the project context`() {
    val rustup = tool("bin/rustup")
    val installed = tool("installed toolchain/bin/rustc")
    val resolver = ToolchainResolver(root, mapOf("PATH" to rustup.parent.toString()), ToolProbe { command, _ ->
      assertEquals(listOf("which", "--toolchain", "nightly-local", "rustc"), command.arguments)
      assertEquals(root, command.workingDirectory)
      assertEquals("0", command.environment["RUSTUP_AUTO_INSTALL"])
      ProcessOutput(installed.toString() + "\n", "", 0, false, false)
    })
    val command = resolver.rustCommand(CompilerTool.RUSTC, RustToolchain(toolchain = "nightly-local"), listOf("--version"), indicator)
    assertEquals(installed, command.executable)
    assertEquals("nightly-local", command.environment["RUSTUP_TOOLCHAIN"])
  }

  @Test
  fun `missing rustup component is not replaced by a different toolchain from PATH`() {
    tool("bin/rustup")
    tool("bin/rustc")
    val resolver = ToolchainResolver(root, mapOf("PATH" to root.resolve("bin").toString()), ToolProbe { _, _ ->
      ProcessOutput("", "component is not installed", 1, false, false)
    })
    assertThrows(IllegalArgumentException::class.java) {
      resolver.rustCommand(CompilerTool.RUSTC, RustToolchain(), emptyList(), indicator)
    }
  }

  @Test
  fun `relative compiler paths resolve against source roots and Vex uses the same compiler`() {
    val compiler = tool("Wave 소스/target/debug/wavec")
    val vex = tool("bin/vex")
    val cwd = Files.createDirectory(root.resolve("작업 폴더"))
    val profile = EcosystemProfile(wavec = "target/debug/wavec", waveRoot = "Wave 소스", vex = vex.toString(), workingDirectory = "작업 폴더")
    val resolver = ToolchainResolver(root, mapOf("PATH" to "", "VEX_WAVEC" to "wrong-compiler"))
    val direct = resolver.ecosystemCommand(CompilerTool.WAVEC, profile, listOf("check", "hello world.wave"))
    val throughVex = resolver.ecosystemCommand(CompilerTool.VEX, profile, listOf("build"))
    assertEquals(compiler, direct.executable)
    assertEquals(direct.executable.toString(), throughVex.environment["VEX_WAVEC"])
    assertEquals(cwd, throughVex.workingDirectory)
    assertEquals(listOf("check", "hello world.wave"), direct.arguments)
  }

  @Test
  fun `invalid explicit paths never silently fall back and relative PATH is ignored`() {
    val resolver = ToolchainResolver(root, mapOf("PATH" to "."))
    tool("wavec")
    assertThrows(IllegalArgumentException::class.java) {
      resolver.ecosystemCommand(CompilerTool.WAVEC, EcosystemProfile(wavec = "missing"), emptyList())
    }
    assertThrows(IllegalArgumentException::class.java) {
      resolver.ecosystemCommand(CompilerTool.WAVEC, EcosystemProfile(), emptyList())
    }
  }

  @Test
  fun `profile selection and Rust overrides survive XML serialization`() {
    val state = ToolchainState(
      rust = RustToolchain(toolchain = "nightly-local", cargo = "/tools/cargo", rustfmt = "/tools/rustfmt"),
      profiles = listOf(EcosystemProfile(), EcosystemProfile(id = "dev", name = "개발", wavec = "bin/wavec", target = "x86_64")),
      activeProfileId = "dev",
    )
    val restored = XmlSerializer.deserialize(XmlSerializer.serialize(state), ToolchainState::class.java)
    assertEquals(state, restored)
    assertEquals("bin/wavec", restored.activeProfile()!!.wavec)
    val settings = ToolchainSettings()
    settings.update(restored)
    assertTrue(settings.stateModificationCount > 0)
    assertEquals(state, settings.state)
  }

  @Test
  fun `version probe preserves argument boundaries cwd and environment without shell interpolation`() {
    assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"))
    val binary = tool("fake 도구/rustc", "#!/bin/sh\nprintf '%s\\n' \"\$1\" \"\$2\" \"\$PWD\" \"\$VEX_WAVEC\"\n")
    val argument = "hello world; touch SHOULD_NOT_EXIST"
    val command = ToolCommand(binary, listOf(argument, "한글"), root, mapOf("VEX_WAVEC" to "compiler with spaces"))
    val output = LocalToolProbe.run(command, indicator)
    assertEquals(0, output.exitCode)
    assertEquals(listOf(argument, "한글", root.toString(), "compiler with spaces"), output.stdoutLines)
    assertFalse(Files.exists(root.resolve("SHOULD_NOT_EXIST")))
  }

  @Test
  fun `probe output is bounded`() {
    assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"))
    val binary = tool("noisy", "#!/bin/sh\ni=0\nwhile [ \"\$i\" -lt 3000 ]; do printf '0123456789'; i=\$((i+1)); done\n")
    val output = LocalToolProbe.run(ToolCommand(binary, emptyList(), root, emptyMap()), indicator)
    assertEquals(0, output.exitCode)
    assertTrue(output.stdout.length <= 16 * 1024)
  }

  @Test
  fun `failed or incompatible version probes are reported`() {
    val rustc = tool("rustc")
    val settings = ToolchainState(rust = RustToolchain(rustc = rustc.toString()))
    val resolver = ToolchainResolver(root, emptyMap(), ToolProbe { _, _ ->
      ProcessOutput("a different program 1.0", "", 0, false, false)
    })
    val unexpected = WaveBundle.message("toolchains.unexpected.version", "a different program 1.0")
    assertTrue(resolver.inspect(settings, indicator).contains(unexpected))
    val failed = ToolchainResolver(root, emptyMap(), ToolProbe { _, _ ->
      ProcessOutput("", "cannot start compiler", 17, false, false)
    })
    assertTrue(failed.inspect(settings, indicator).contains("cannot start compiler"))
  }

  @Test
  fun `cancelled probes do not start the executable`() {
    assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"))
    val binary = tool("fake rustc", "#!/bin/sh\nprintf ran > probe-was-started\n")
    indicator.cancel()
    assertThrows(com.intellij.openapi.progress.ProcessCanceledException::class.java) {
      LocalToolProbe.run(ToolCommand(binary, emptyList(), root, emptyMap()), indicator)
    }
    assertFalse(Files.exists(root.resolve("probe-was-started")))
  }
}
