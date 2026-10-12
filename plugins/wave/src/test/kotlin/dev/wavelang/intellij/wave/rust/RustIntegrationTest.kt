package dev.wavelang.intellij.wave.rust

import dev.wavelang.intellij.wave.cargo.CargoPackage
import dev.wavelang.intellij.wave.cargo.CargoTarget
import dev.wavelang.intellij.wave.cargo.CargoWorkspace
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class RustIntegrationTest {
  @TempDir lateinit var root: Path

  @Test fun `Rust analysis links explicit project metadata and disables automatic compilation`() {
    val manifest = root.resolve("컴파일러 소스/Cargo.toml")
    val options = rustAnalyzerOptions(manifest)
    assertEquals(listOf(manifest.toString()), options["linkedProjects"])
    assertEquals(false, options["checkOnSave"])
    val cargo = options["cargo"] as Map<*, *>
    assertEquals(true, cargo["noDeps"])
    assertEquals(mapOf("enable" to false), cargo["buildScripts"])
    assertEquals(mapOf("enable" to false), options["procMacro"])
    assertTrue((cargo["metadataExtraArgs"] as List<*>).contains("--offline"))
  }

  @Test fun `formatting uses target edition and the source directory for rustfmt config discovery`() {
    val source = root.resolve("pkg/src/main.rs")
    val pkg = CargoPackage("package-id", "pkg", root.resolve("pkg/Cargo.toml"),
      listOf(CargoTarget("compiler", listOf("bin"), source, "2024")))
    val workspace = CargoWorkspace(root, root.resolve("target"), listOf(pkg))
    assertEquals("2024", rustEdition(workspace, root.resolve("pkg/src/lexer/token.rs")))
    assertEquals(listOf("--emit", "stdout", "--edition", "2024", "--config-path", source.parent.toString()),
                 rustfmtArguments(source, "2024"))
    assertThrows(IllegalArgumentException::class.java) { rustEdition(workspace, root.resolve("outside.rs")) }
  }

  @Test fun `formatting never applies failed cancelled or stale results`() {
    assertTrue(canApplyFormatting(4, 4, true, false))
    assertFalse(canApplyFormatting(4, 5, true, false))
    assertFalse(canApplyFormatting(4, 4, false, false))
    assertFalse(canApplyFormatting(4, 4, true, true))
  }
}
