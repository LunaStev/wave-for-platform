package dev.wavelang.intellij.wave.toolchain

import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import dev.wavelang.intellij.wave.WaveBundle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.io.OutputStream

/** The Run window receives a handler immediately; resolution and launch happen in the project's background scope. */
internal class ToolRunProcessHandler(
  private val project: Project,
  private val prepare: suspend (EmptyProgressIndicator) -> ToolCommand,
  private val options: ToolExecutionOptions,
  private val output: (String, Boolean) -> Unit,
  private val flush: () -> Unit,
) : ProcessHandler() {
  private val indicator = EmptyProgressIndicator()
  @Volatile private var job: Job? = null

  override fun startNotify() {
    super.startNotify()
    val launched = project.service<ToolProcessService>().launch {
      var exitCode = 130
      try {
        indicator.checkCanceled()
        val command = prepare(indicator)
        val result = project.service<ToolProcessService>().execute(command, options, indicator, output)
        exitCode = when (result.status) {
          ToolStatus.SUCCESS, ToolStatus.NONZERO_EXIT -> result.exitCode ?: 1
          ToolStatus.LAUNCH_FAILED -> 127
          ToolStatus.TIMED_OUT -> 124
          ToolStatus.CANCELLED -> 130
        }
        flush()
        val status = WaveBundle.message("rust.command.status.${result.status.name.lowercase()}")
        notifyTextAvailable(WaveBundle.message("rust.command.finished", status, exitCode) + "\n", ProcessOutputTypes.SYSTEM)
        if (result.status == ToolStatus.LAUNCH_FAILED) {
          notifyTextAvailable(result.stderr + "\n", ProcessOutputTypes.STDERR)
        }
      }
      catch (e: CancellationException) {
        notifyTextAvailable(WaveBundle.message("toolchains.cancelled") + "\n", ProcessOutputTypes.SYSTEM)
        throw e
      }
      catch (e: Exception) {
        exitCode = if (indicator.isCanceled) 130 else 127
        notifyTextAvailable((e.message ?: WaveBundle.message("rust.command.failed")) + "\n", ProcessOutputTypes.STDERR)
      }
      finally {
        notifyProcessTerminated(exitCode)
      }
    }
    job = launched
    launched.invokeOnCompletion { if (!isProcessTerminated) notifyProcessTerminated(130) }
    if (indicator.isCanceled) launched.cancel()
  }

  override fun destroyProcessImpl() {
    indicator.cancel()
    job?.cancel()
  }

  override fun detachProcessImpl() = destroyProcessImpl()
  override fun detachIsDefault(): Boolean = false
  override fun getProcessInput(): OutputStream? = null
}
