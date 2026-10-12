package dev.wavelang.intellij.wave.toolchain

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.EelPlatform
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

@TestApplication
class ToolProcessExecutorTest {
  @TempDir lateinit var root: Path

  private fun command(script: String): ToolCommand {
    assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"))
    val binary = root.resolve("fake tool")
    Files.writeString(binary, "#!/bin/sh\n$script\n")
    Files.setPosixFilePermissions(binary, PosixFilePermissions.fromString("rwx------"))
    return ToolCommand(binary, emptyList(), root, emptyMap())
  }

  @Test fun `burst output is bounded but optional log is complete and streams stay separate`() = runBlocking {
    val command = command("i=0; while [ \"\$i\" -lt 1000 ]; do printf '한글0123456789'; i=\$((i+1)); done; printf 'stderr only' >&2")
    val log = root.resolve("complete.log")
    val result = ToolProcessExecutor.execute(command, ToolExecutionOptions(captureLimit = 128, log = log))
    assertEquals(ToolStatus.SUCCESS, result.status)
    assertTrue(result.truncated)
    assertTrue(result.stdout.length + result.stderr.length <= 128)
    assertTrue(Files.readString(log).contains("stderr only"))
    assertTrue(Files.size(log) > 10_000)
    assertFalse(result.stdout.contains("stderr only"))
  }

  @Test fun `launch failure nonzero timeout and cancellation have distinct results`() = runBlocking {
    val missing = ToolCommand(root.resolve("missing"), emptyList(), root, emptyMap())
    assertEquals(ToolStatus.LAUNCH_FAILED, ToolProcessExecutor.execute(missing).status)
    val failed = ToolProcessExecutor.execute(command("printf 'failure' >&2; exit 17"))
    assertEquals(ToolStatus.NONZERO_EXIT, failed.status)
    assertEquals(17, failed.exitCode)
    assertEquals("failure", failed.stderr)
    assertEquals(ToolStatus.TIMED_OUT, withTimeout(5000) {
      ToolProcessExecutor.execute(command("sleep 60"), ToolExecutionOptions(timeoutMillis = 250))
    }.status)
    val indicator = EmptyProgressIndicator().apply { cancel() }
    val cancelled = ToolProcessExecutor.execute(command("touch was-started"), indicator = indicator)
    assertEquals(ToolStatus.CANCELLED, cancelled.status)
    assertFalse(Files.exists(root.resolve("was-started")))
  }

  @Test fun `cancellation kills owned child and releases inherited pipes`() = runBlocking {
    val indicator = EmptyProgressIndicator()
    val work = async {
      ToolProcessExecutor.execute(command("sleep 60 & child=\$!; printf '%s' \"\$child\" > child.pid; wait"), indicator = indicator)
    }
    withTimeout(5000) { while (!Files.exists(root.resolve("child.pid"))) delay(20) }
    delay(100)
    val child = Files.readString(root.resolve("child.pid")).toLong()
    indicator.cancel()
    assertEquals(ToolStatus.CANCELLED, withTimeout(5000) { work.await() }.status)
    withTimeout(5000) { while (ProcessHandle.of(child).map { it.isAlive }.orElse(false)) delay(20) }
  }

  @Test fun `early parent exit does not wait for a hanging child stdout`() = runBlocking {
    assumeTrue(localEel.platform is EelPlatform.Linux)
    val result = withTimeout(5000) {
      ToolProcessExecutor.execute(command("sleep 60 & printf '%s' \"\$!\" > child.pid; exit 0"))
    }
    assertEquals(ToolStatus.SUCCESS, result.status)
    val child = Files.readString(root.resolve("child.pid")).toLong()
    withTimeout(5000) { while (ProcessHandle.of(child).map { it.isAlive }.orElse(false)) delay(20) }
  }

  @Test fun `stdin preserves unsaved Unicode and CRLF without writing the source file`() = runBlocking {
    val input = "fn main() { println!(\"한글 😀\"); }\r\n"
    val source = root.resolve("source.rs")
    Files.writeString(source, "original on disk")
    val result = ToolProcessExecutor.execute(command("cat"), ToolExecutionOptions(input = input))
    assertEquals(input, result.requireSuccess())
    assertEquals("original on disk", Files.readString(source))
  }
}
