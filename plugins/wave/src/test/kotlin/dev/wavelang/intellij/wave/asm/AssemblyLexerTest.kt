package dev.wavelang.intellij.wave.asm

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AssemblyLexerTest {
  private data class LexedToken(val type: IElementType, val text: String)

  private fun lexAll(source: String): List<LexedToken> {
    val lexer = AssemblyLexer()
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
  fun threeRepresentativeAssemblyDialectsAreCovered() {
    val source = """
      .globl _start
      _start:
       mov rax, 1
       add x0, x1, x2
       addi a0, a0, 1
       cmp x0, #1
    """.trimIndent()

    val tokens = lexAll(source).filter { it.type != TokenType.WHITE_SPACE }

    assertTrue(tokens.any { it.type == AssemblyTokens.DIRECTIVE && it.text == ".globl" })
    assertTrue(tokens.any { it.type == AssemblyTokens.LABEL && it.text == "_start" })
    assertTrue(tokens.any { it.type == AssemblyTokens.INSTRUCTION && it.text.lowercase() == "mov" })
    assertTrue(tokens.any { it.type == AssemblyTokens.INSTRUCTION && it.text.lowercase() == "add" })
    assertTrue(tokens.any { it.type == AssemblyTokens.INSTRUCTION && it.text.lowercase() == "addi" })
    assertTrue(tokens.any { it.type == AssemblyTokens.REGISTER && it.text.lowercase() == "rax" })
    assertTrue(tokens.any { it.type == AssemblyTokens.REGISTER && it.text.lowercase() == "x0" })
    assertTrue(tokens.any { it.type == AssemblyTokens.REGISTER && it.text.lowercase() == "a0" })
    assertTrue(tokens.any { it.type == AssemblyTokens.OPERATOR && it.text == "#" })
  }
}
