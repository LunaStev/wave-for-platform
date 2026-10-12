package dev.wavelang.intellij.wave.toolchain

import com.intellij.execution.process.OSProcessUtil
import com.intellij.execution.process.UnixProcessManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.spawnProcess
import dev.wavelang.intellij.wave.WaveBundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

internal enum class ToolStatus { SUCCESS, NONZERO_EXIT, LAUNCH_FAILED, TIMED_OUT, CANCELLED }

internal data class ToolResult(
  val status: ToolStatus,
  val exitCode: Int? = null,
  val stdout: String = "",
  val stderr: String = "",
  val truncated: Boolean = false,
) {
  fun requireSuccess(): String {
    check(status == ToolStatus.SUCCESS) {
      val state = WaveBundle.message("rust.command.status.${status.name.lowercase()}")
      WaveBundle.message("rust.command.unsuccessful", state, stderr.take(2048))
    }
    check(!truncated) { WaveBundle.message("rust.command.output.limit") }
    return stdout
  }
}

internal data class ToolExecutionOptions(
  val timeoutMillis: Long = 30_000,
  val captureLimit: Int = 128 * 1024,
  val input: String? = null,
  val log: Path? = null,
)

/** Project scope owns every command; callers own only their individual cancellation and output adapters. */
@Service(Service.Level.PROJECT)
internal class ToolProcessService(val scope: CoroutineScope) {
  fun launch(action: suspend CoroutineScope.() -> Unit) = scope.launch(Dispatchers.IO, block = action)

  suspend fun execute(
    command: ToolCommand,
    options: ToolExecutionOptions = ToolExecutionOptions(),
    indicator: ProgressIndicator? = null,
    output: (String, Boolean) -> Unit = { _, _ -> },
  ): ToolResult {
    val work = scope.async(Dispatchers.IO) { ToolProcessExecutor.execute(command, options, indicator, output) }
    return try {
      work.await()
    }
    finally {
      work.cancel()
    }
  }

}

/** Local-host execution, argument arrays, independent streams, and bounded capture. Adapters decide when commands are requested. */
internal object ToolProcessExecutor {
  suspend fun execute(
    command: ToolCommand,
    options: ToolExecutionOptions = ToolExecutionOptions(),
    indicator: ProgressIndicator? = null,
    output: (String, Boolean) -> Unit = { _, _ -> },
  ): ToolResult = withContext(Dispatchers.IO) {
    require(options.timeoutMillis > 0 && options.captureLimit > 0)
    require(command.workingDirectory.getEelDescriptor() == LocalEelDescriptor)
    require(command.executable.getEelDescriptor() == LocalEelDescriptor)
    val capture = Capture(options.captureLimit)
    val owned = ConcurrentHashMap<Long, ProcessHandle>()
    var process: Process? = null
    val log = options.log?.let { Files.newBufferedWriter(it, Charsets.UTF_8) }
    try {
      val completed = withTimeoutOrNull(options.timeoutMillis) {
        coroutineScope {
          indicator?.checkCanceled()
          require(Files.isRegularFile(command.executable) && Files.isExecutable(command.executable)) {
            WaveBundle.message("toolchains.not.executable", command.executable)
          }
          require(Files.isDirectory(command.workingDirectory)) {
            WaveBundle.message("toolchains.invalid.directory", command.workingDirectory)
          }
          // An isolated Linux process group lets us terminate descendants even after an immediate parent exit.
          val sessionLauncher = if (localEel.platform is EelPlatform.Linux) {
            listOf(Path.of("/usr/bin/setsid"), Path.of("/bin/setsid")).firstOrNull { Files.isExecutable(it) }
              ?: throw IllegalStateException(WaveBundle.message("toolchains.session.required"))
          }
          else null
          val started = localEel.exec.spawnProcess((sessionLauncher ?: command.executable).asEelPath())
            .args(if (sessionLauncher == null) command.arguments else listOf("--", command.executable.toString()) + command.arguments)
            .workingDirectory(command.workingDirectory.asEelPath())
            .env(command.environment)
            .scope(this)
            .eelIt()
            .convertToJavaProcess()
          process = started
          var groupTerminated = false
          fun terminateGroup() {
            if (sessionLauncher != null && !groupTerminated) {
              UnixProcessManager.sendSignalToGroup(started.pid().toInt(), UnixProcessManager.SIGKILL)
              groupTerminated = true
            }
          }
          fun rememberChildren() {
            owned.entries.removeIf { !it.value.isAlive }
            started.toHandle().descendants().use { children -> children.forEach { owned[it.pid()] = it } }
          }
          rememberChildren()
          suspend fun read(stream: InputStream, error: Boolean) = withContext(Dispatchers.IO) {
            stream.use {
              val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
              val bytes = ByteBuffer.allocate(8192)
              val chars = CharBuffer.allocate(8192)
              while (true) {
                ensureActive()
                val available = stream.available()
                val end = available == 0 && !started.isAlive
                if (available > 0) {
                  val count = stream.read(bytes.array(), bytes.position(), minOf(available, bytes.remaining()))
                  if (count > 0) bytes.position(bytes.position() + count)
                }
                bytes.flip()
                decoder.decode(bytes, chars, end)
                if (end) decoder.flush(chars)
                bytes.compact()
                chars.flip()
                if (chars.hasRemaining()) {
                  val text = chars.toString()
                  capture.append(text, error)
                  if (log != null) synchronized(log) { log.write(text) }
                  output(text, error)
                }
                chars.clear()
                if (end) break
                if (available == 0) delay(10)
              }
            }
          }
          try {
            val stdout = async { read(started.inputStream, false) }
            val stderr = async { read(started.errorStream, true) }
            val stdin = async {
              started.outputStream.use { stream -> options.input?.let { stream.write(it.toByteArray(Charsets.UTF_8)) } }
            }
            while (started.isAlive) {
              indicator?.checkCanceled()
              rememberChildren()
              delay(25)
            }
            // Cargo may exit while a child still holds inherited pipes. Such a child cannot outlive the owned command.
            terminateGroup()
            owned.values.filter { it.isAlive }.forEach { it.destroyForcibly() }
            stdin.await()
            stdout.await()
            stderr.await()
            val code = started.exitValue()
            capture.result(if (code == 0) ToolStatus.SUCCESS else ToolStatus.NONZERO_EXIT, code)
          }
          finally {
            // Terminate owned work before coroutineScope joins its I/O tasks on cancellation or project disposal.
            terminateGroup()
            if (started.isAlive) OSProcessUtil.killProcessTree(started)
            owned.values.filter { it.isAlive }.forEach { it.destroyForcibly() }
            if (started.isAlive) started.destroyForcibly()
            started.inputStream.close()
            started.errorStream.close()
            started.outputStream.close()
          }
        }
      }
      completed ?: capture.result(ToolStatus.TIMED_OUT)
    }
    catch (_: ProcessCanceledException) {
      capture.result(ToolStatus.CANCELLED)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      capture.append(e.message ?: e.javaClass.simpleName, true)
      capture.result(ToolStatus.LAUNCH_FAILED)
    }
    finally {
      process?.let { started ->
        if (started.isAlive) OSProcessUtil.killProcessTree(started)
        owned.values.filter { it.isAlive }.forEach { it.destroyForcibly() }
        if (started.isAlive) started.destroyForcibly()
        started.inputStream.close()
        started.errorStream.close()
        started.outputStream.close()
      }
      log?.close()
    }
  }

  private class Capture(private val limit: Int) {
    private val stdout = StringBuilder()
    private val stderr = StringBuilder()
    private var truncated = false

    @Synchronized
    fun append(text: String, error: Boolean) {
      val retained = text.take((limit - stdout.length - stderr.length).coerceAtLeast(0))
      (if (error) stderr else stdout).append(retained)
      if (retained.length != text.length) truncated = true
    }

    @Synchronized
    fun result(status: ToolStatus, code: Int? = null) = ToolResult(status, code, stdout.toString(), stderr.toString(), truncated)
  }
}
