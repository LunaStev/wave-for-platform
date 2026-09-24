package dev.wavelang.intellij.wave.ld

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LinkerScriptLexerTest {
  private data class LexedToken(
    val type: IElementType,
    val text: String,
    val start: Int,
    val end: Int,
  )

  private fun lexAll(source: String): List<LexedToken> {
    val lexer = LinkerScriptLexer()
    lexer.start(source, 0, source.length, 0)

    val out = mutableListOf<LexedToken>()
    while (true) {
      val type = lexer.tokenType ?: break
      val text = source.substring(lexer.tokenStart, lexer.tokenEnd)
      out += LexedToken(type, text, lexer.tokenStart, lexer.tokenEnd)
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

  @Test
  fun standaloneLocationCountersAreOperators() {
    val source = "SECTIONS { . = 0x1000; .text : { *(.text) } . = ALIGN(16); PROVIDE(end = .); }"
    val tokens = lexAll(source)

    assertEquals(
      listOf(
        LexedToken(LinkerScriptTokens.OPERATOR, ".", 11, 12),
        LexedToken(LinkerScriptTokens.OPERATOR, ".", 44, 45),
        LexedToken(LinkerScriptTokens.OPERATOR, ".", 73, 74),
      ),
      tokens.filter { it.type == LinkerScriptTokens.OPERATOR && it.text == "." },
    )
    assertTrue(tokens.any { it.type == LinkerScriptTokens.SECTION && it.text == ".text" })
    assertFalse(tokens.any { it.type == TokenType.BAD_CHARACTER })
  }

  @Test
  fun standaloneLocationCounterHandlesPunctuationAndEof() {
    assertEquals(
      listOf(LexedToken(LinkerScriptTokens.OPERATOR, ".", 0, 1)),
      lexAll("."),
    )

    val tokens = lexAll(".;.")
    assertEquals(
      listOf(
        LexedToken(LinkerScriptTokens.OPERATOR, ".", 0, 1),
        LexedToken(LinkerScriptTokens.OPERATOR, ".", 2, 3),
      ),
      tokens.filter { it.type == LinkerScriptTokens.OPERATOR && it.text == "." },
    )
    assertFalse(tokens.any { it.type == TokenType.BAD_CHARACTER })
  }
}
