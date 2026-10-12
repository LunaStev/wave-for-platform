package dev.wavelang.intellij.wave.cargo

import com.google.gson.Gson
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.junit5.TestApplication
import dev.wavelang.intellij.wave.toolchain.ToolCommand
import dev.wavelang.intellij.wave.toolchain.ToolExecutionOptions
import dev.wavelang.intellij.wave.toolchain.ToolProcessExecutor
import kotlinx.coroutines.runBlocking
import org.jdom.Element
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

@TestApplication
class CargoIntegrationTest {
  @TempDir lateinit var root: Path

  private fun metadata(virtual: Boolean): String {
    val quote = { value: String -> Gson().toJson(value) }
    val rootPackage = if (virtual) "" else """{"id":"root-id","name":"root","manifest_path":${quote(root.resolve("Cargo.toml").toString())},
      "targets":[{"name":"wavec","kind":["bin"],"src_path":${quote(root.resolve("src/main.rs").toString())},"edition":"2024"}]},"""
    return """{"version":1,"workspace_root":${quote(root.toString())},"target_directory":${quote(root.resolve("custom output").toString())},
      "workspace_members":["member-id"${if (virtual) "" else ",\"root-id\""}],"packages":[$rootPackage
      {"id":"member-id","name":"lexer","manifest_path":${quote(root.resolve("lexer/Cargo.toml").toString())},
       "targets":[{"name":"lexer","kind":["lib"],"src_path":${quote(root.resolve("lexer/src/lib.rs").toString())},"edition":"2021"}]},
      {"id":"dependency-id","name":"external","manifest_path":"/cache/Cargo.toml","targets":[]}]}"""
  }

  @Test fun `root and virtual workspaces preserve target identity and exclude nonmembers`() {
    for (virtual in listOf(false, true)) {
      val model = parseCargoMetadata(metadata(virtual))
      assertEquals(root, model.root)
      assertEquals(root.resolve("custom output"), model.targetDirectory)
      assertEquals(if (virtual) 1 else 2, model.packages.size)
      assertEquals("member-id", model.packageFor(root.resolve("lexer/src/token.rs"))?.id)
      assertEquals("2021", model.packages.first { it.name == "lexer" }.targets.single().edition)
    }
    assertThrows(Exception::class.java) { parseCargoMetadata("not metadata") }
    assertThrows(IllegalArgumentException::class.java) { parseCargoMetadata(metadata(true).replace("\"version\":1", "\"version\":2")) }
  }

  @Test fun `nested manifest discovery avoids output and supports opening a subdirectory`() {
    val nested = Files.createDirectories(root.resolve("tools/compiler/src"))
    val manifest = nested.parent.resolve("Cargo.toml")
    Files.writeString(manifest, "[workspace]\n")
    Files.createDirectories(root.resolve("target/fake"))
    Files.writeString(root.resolve("target/fake/Cargo.toml"), "ignored")
    assertEquals(manifest, nearestCargoManifest(nested))
    assertEquals(listOf(manifest), discoverCargoManifests(root))
    assertTrue(affectsCargoModel("/project/.cargo/config.toml"))
    assertFalse(affectsCargoModel("/project/src/main.rs"))
  }

  @Test fun `Cargo configuration round trip preserves feature flags program arguments and environment`() {
    val value = CargoRunSpec("test", "Rust 소스/Cargo.toml", "lexer", "lib", profile = "release", features = "trace,logging",
      noDefaultFeatures = true, workingDirectory = "작업 폴더", testFilter = "module::case with spaces",
      testArguments = listOf("--exact", "value with spaces", "한글; echo bad"), environment = mapOf("RUST_LOG" to "debug=trace"),
      timeoutSeconds = 42, offline = false, log = "logs/compiler.log")
    val element = Element("configuration")
    value.write(element)
    assertEquals(value, CargoRunSpec.read(element))
    val args = value.arguments(root.resolve(value.manifest))
    assertEquals("test", args.first())
    assertTrue(args.containsAll(listOf("--profile", "release", "--features", "trace,logging", "--no-default-features")))
    assertFalse(args.contains("--offline"))
    assertEquals(value.testArguments, args.drop(args.indexOf("--") + 1))
    assertEquals("debug=trace", parseEnvironment("RUST_LOG=debug=trace")["RUST_LOG"])
    assertThrows(IllegalArgumentException::class.java) { value.copy(command = "check").validate() }
    assertThrows(IllegalArgumentException::class.java) { value.copy(timeoutSeconds = 0).validate() }
  }

  @Test fun `fake Cargo sees argument boundaries working directory and selected environment`() = runBlocking {
    assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"))
    val binary = root.resolve("fake cargo")
    Files.writeString(binary, "#!/bin/sh\nprintf '%s\\n' \"\$PWD\" \"\$RUSTC\" \"\$@\"\n")
    Files.setPosixFilePermissions(binary, PosixFilePermissions.fromString("rwx------"))
    val spec = CargoRunSpec("test", "경로 공백/Cargo.toml", testFilter = "한글; touch BAD", testArguments = listOf("--exact"))
    val arguments = spec.arguments(root.resolve(spec.manifest))
    val result = ToolProcessExecutor.execute(ToolCommand(binary, arguments, root, mapOf("RUSTC" to "selected rustc")),
      ToolExecutionOptions(), EmptyProgressIndicator())
    assertEquals(listOf(root.toString(), "selected rustc") + arguments, result.requireSuccess().trimEnd().lines())
    assertFalse(Files.exists(root.resolve("BAD")))
  }

  @Test fun `diagnostics preserve Unicode primary secondary and macro locations and tolerate unknown events`() {
    val message = """{"reason":"compiler-message","message":{"level":"error","message":"bad type","rendered":null,
      "spans":[{"file_name":"src/한 글.rs","line_start":2,"column_start":4,"is_primary":true,"label":"here","expansion":null}],
      "children":[{"spans":[{"file_name":"src/helper.rs","line_start":1,"column_start":2,"is_primary":false,
      "expansion":{"span":{"file_name":"src/main.rs","line_start":7,"column_start":1,"is_primary":false}}}],"children":[]}]}}"""
    val diagnostic = requireNotNull(parseCargoDiagnostic(message, root))
    assertEquals(3, diagnostic.spans.size)
    assertEquals(root.resolve("src/한 글.rs"), diagnostic.spans.first().file)
    assertTrue(diagnostic.spans.first().primary)
    assertEquals(4, rustColumnOffset("a😀한z", 4))
    assertNull(parseCargoDiagnostic("{broken", root))
    assertNull(parseCargoDiagnostic("{\"reason\":\"compiler-artifact\"}", root))
  }
}
