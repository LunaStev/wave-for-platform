package dev.wavelang.intellij.wave.cargo

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Path

internal data class CargoSpan(val file: Path, val line: Int, val column: Int, val primary: Boolean, val label: String?)
internal data class CargoDiagnostic(val text: String, val spans: List<CargoSpan>)

internal fun parseCargoDiagnostic(line: String, root: Path): CargoDiagnostic? {
  return try {
    val event = JsonParser.parseString(line).asJsonObject
    if (event.get("reason")?.asString != "compiler-message") return null
    val message = event.getAsJsonObject("message")
    val spans = ArrayList<CargoSpan>()
    fun collect(node: JsonObject, depth: Int) {
      if (depth > 8 || spans.size >= 256) return
      node.getAsJsonArray("spans")?.forEach { value ->
        if (spans.size >= 256) return@forEach
        val span = value.asJsonObject
        val path = Path.of(span.string("file_name"))
        val lineNumber = span.get("line_start").asInt
        val column = span.get("column_start").asInt
        if (lineNumber > 0 && column > 0) {
          spans.add(CargoSpan((if (path.isAbsolute) path else root.resolve(path)).normalize(),
                              lineNumber, column, span.get("is_primary")?.asBoolean == true,
                              span.get("label")?.takeUnless { it.isJsonNull }?.asString))
        }
        span.get("expansion")?.takeIf { it.isJsonObject }?.asJsonObject?.getAsJsonObject("span")?.let { expansion ->
          val wrapper = JsonObject().apply { add("spans", com.google.gson.JsonArray().apply { add(expansion) }) }
          collect(wrapper, depth + 1)
        }
      }
      node.getAsJsonArray("children")?.forEach { collect(it.asJsonObject, depth + 1) }
    }
    collect(message, 0)
    val rendered = message.get("rendered")?.takeUnless { it.isJsonNull }?.asString
    val text = rendered ?: "${message.string("level")}: ${message.string("message")}\n"
    CargoDiagnostic(text.replace(Regex("\u001B\\[[0-9;]*m"), ""), spans.distinct().sortedByDescending { it.primary })
  }
  catch (_: Exception) {
    null // Unknown/malformed messages remain visible as raw console output.
  }
}

/** Rust columns count Unicode scalar values. Convert at navigation time against the current editor document. */
internal fun rustColumnOffset(line: String, column: Int): Int =
  line.offsetByCodePoints(0, (column - 1).coerceIn(0, line.codePointCount(0, line.length)))

internal class CargoConsole(private val console: ConsoleView, private val root: Path) {
  private val pending = StringBuilder()
  private var passthrough = false

  @Synchronized
  fun accept(text: String, error: Boolean) {
    if (error) {
      console.print(text, ConsoleViewContentType.ERROR_OUTPUT)
      return
    }
    var start = 0
    while (start < text.length) {
      val end = text.indexOf('\n', start)
      val piece = text.substring(start, if (end < 0) text.length else end + 1)
      if (passthrough) console.print(piece, ConsoleViewContentType.NORMAL_OUTPUT)
      else {
        pending.append(piece)
        if (pending.length > 1024 * 1024) {
          console.print(pending.toString(), ConsoleViewContentType.NORMAL_OUTPUT)
          pending.setLength(0)
          passthrough = true
        }
      }
      if (end >= 0) {
        if (!passthrough) printLine(pending.toString())
        pending.setLength(0)
        passthrough = false
      }
      start += piece.length
    }
  }

  @Synchronized
  fun flush() {
    if (pending.isNotEmpty()) printLine(pending.toString())
    pending.setLength(0)
  }

  private fun printLine(line: String) {
    val diagnostic = parseCargoDiagnostic(line, root)
    if (diagnostic == null) {
      console.print(line, ConsoleViewContentType.NORMAL_OUTPUT)
      return
    }
    console.print(diagnostic.text, ConsoleViewContentType.NORMAL_OUTPUT)
    if (!diagnostic.text.endsWith('\n')) console.print("\n", ConsoleViewContentType.NORMAL_OUTPUT)
    for (span in diagnostic.spans) {
      val target = "${span.file}:${span.line}:${span.column}"
      console.printHyperlink(target, HyperlinkInfo { targetProject ->
        val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(span.file) ?: return@HyperlinkInfo
        val document = FileDocumentManager.getInstance().getDocument(file)
        val lineIndex = span.line - 1
        if (document != null && lineIndex < document.lineCount) {
          val start = document.getLineStartOffset(lineIndex)
          val lineText = document.immutableCharSequence.subSequence(start, document.getLineEndOffset(lineIndex)).toString()
          OpenFileDescriptor(targetProject, file, start + rustColumnOffset(lineText, span.column)).navigate(true)
        }
        else OpenFileDescriptor(targetProject, file, lineIndex, 0).navigate(true)
      })
      console.print(span.label?.let { " $it\n" } ?: "\n", ConsoleViewContentType.NORMAL_OUTPUT)
    }
  }
}
