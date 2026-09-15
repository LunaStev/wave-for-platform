package dev.wavelang.intellij.wave.ld

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LinkerScriptLexerTest {
  private data class LexedToken(val type: IElementType, val text: String)

  private fun lexAll(source: String): List<LexedToken> {
    val lexer = LinkerScriptLexer()
    lexer.start(source, 0, source.length, 0)

    val out = mutableListOf<LexedToken>()
    while (true) {
      val type = lexer.tokenType ?: break
      val text = source.substring(lexer.tokenStart, lexer.tokenEnd)
      out += LexedToken(type, text)
      lexer.advance()
    }
    return out
  }

  @Test
  fun gnuLdCoreConstructsAreHighlighted() {
    val source = """
      ENTRY(_start)
      MEMORY
      {
       ROM (rx) : ORIGIN = 0x00100000, LENGTH = 256K
      }
      SECTIONS
      {
       .text : { KEEP(*(.text .text.*)) } > ROM
       PROVIDE(end = .);
      }
      /* linker comment */
    """.trimIndent()

    val tokens = lexAll(source).filter { it.type != TokenType.WHITE_SPACE }

    assertTrue(tokens.any { it.type == LinkerScriptTokens.KEYWORD && it.text.uppercase() == "ENTRY" })
    assertTrue(tokens.any { it.type == LinkerScriptTokens.KEYWORD && it.text.uppercase() == "MEMORY" })
    assertTrue(tokens.any { it.type == LinkerScriptTokens.KEYWORD && it.text.uppercase() == "SECTIONS" })
    assertTrue(tokens.any { it.type == LinkerScriptTokens.KEYWORD && it.text.uppercase() == "KEEP" })
    assertTrue(tokens.any { it.type == LinkerScriptTokens.BUILTIN && it.text.uppercase() == "ORIGIN" })
    assertTrue(tokens.any { it.type == LinkerScriptTokens.BUILTIN && it.text.uppercase() == "LENGTH" })
    assertTrue(tokens.any { it.type == LinkerScriptTokens.SECTION && it.text == ".text" })
    assertTrue(tokens.any { it.type == LinkerScriptTokens.NUMBER && it.text == "0x00100000" })
    assertTrue(tokens.any { it.type == LinkerScriptTokens.NUMBER && it.text == "256K" })
    assertTrue(tokens.any { it.type == LinkerScriptTokens.COMMENT && it.text.startsWith("/*") })
  }
}
