package dev.wavelang.intellij.wave.make

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MakefileLexerTest {
  private data class LexedToken(val type: IElementType, val text: String)

  private fun lexAll(source: String): List<LexedToken> {
    val lexer = MakefileLexer()
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
  fun makefileCoreTokensAreHighlighted() {
    val source = listOf(
      "CC := clang",
      "all: main.o",
      "\t$(CC) -o kernel main.o",
      "include config.mk",
      "# build comment"
    ).joinToString("\n")

    val tokens = lexAll(source).filter { it.type != TokenType.WHITE_SPACE }

    assertTrue(tokens.any { it.type == MakeTokens.ASSIGN && it.text == ":=" })
    assertTrue(tokens.any { it.type == MakeTokens.TARGET && it.text == "all" })
    assertTrue(tokens.any { it.type == MakeTokens.COMMAND && it.text.contains("$(CC)") })
    assertTrue(tokens.any { it.type == MakeTokens.DIRECTIVE && it.text == "include" })
    assertTrue(tokens.any { it.type == MakeTokens.COMMENT && it.text.startsWith("#") })
  }
}
