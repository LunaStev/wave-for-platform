package dev.wavelang.intellij.wave.lsp

import com.intellij.execution.ExecutionException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createFile
import kotlin.io.path.writeText

class WaveLspServerSupportProviderTest {

  @Test
  fun testMissingConfiguredExecutableThrowsError(@TempDir tempDir: Path) {
    val nonExistentPath = tempDir.resolve("missing-agape").absolutePathString()
    val exception = assertThrows(ExecutionException::class.java) {
      resolveExecutable(nonExistentPath)
    }
    assertTrue(exception.message!!.contains("not found at"))
    assertTrue(exception.message!!.contains("missing-agape"))
  }

  @Test
  fun testNonExecutableConfiguredPathThrowsError(@TempDir tempDir: Path) {
    val fileWithSpaces = tempDir.resolve("wave agape dummy").createFile()
    fileWithSpaces.writeText("dummy content")
    
    // Revoking executable permissions is not fully supported on all platforms (e.g., Windows).
    // If the file system rejects the change, skip this test.
    assumeTrue(
      fileWithSpaces.toFile().setExecutable(false),
      "Could not revoke executable permissions on this platform."
    )

    val pathStr = fileWithSpaces.absolutePathString()
    val exception = assertThrows(ExecutionException::class.java) {
      resolveExecutable(pathStr)
    }
    assertTrue(exception.message!!.contains("is not executable"))
    assertTrue(exception.message!!.contains("wave agape dummy"))
  }

  @Test
  fun testValidConfiguredExecutableReturnsPath(@TempDir tempDir: Path) {
    val validExec = tempDir.resolve("valid-agape").createFile()
    validExec.writeText("dummy content")
    
    assumeTrue(
      validExec.toFile().setExecutable(true),
      "Could not grant executable permissions on this platform."
    )

    val pathStr = validExec.absolutePathString()
    val resolved = resolveExecutable(pathStr)
    assertEquals(pathStr, resolved)
  }

  @Test
  fun testEmptyConfiguredPathFallsBackToBundledOrPath() {
    val resolved = resolveExecutable("")
    // Without a bundled executable in the test resources, it will fallback to the default name.
    // The default name is 'wave-agape' or 'wave-agape.exe'.
    val expectedDefault = defaultExecutableName()
    // It should either return the extracted path (if somehow bundled) or fallback to defaultName
    assertTrue(resolved.endsWith(expectedDefault) || resolved == expectedDefault)
  }
}

