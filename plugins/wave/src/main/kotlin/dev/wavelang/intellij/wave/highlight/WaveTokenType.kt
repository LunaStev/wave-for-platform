package dev.wavelang.intellij.wave.highlight

import com.intellij.psi.tree.IElementType
import dev.wavelang.intellij.wave.WaveLanguage

open class WaveTokenType(debugName: String) : IElementType(debugName, WaveLanguage)

object WaveTokens {
  val KEYWORD = WaveTokenType("KEYWORD")
  val DEPRECATED = WaveTokenType("DEPRECATED")
  val TYPE = WaveTokenType("TYPE")
  val BOOL = WaveTokenType("BOOL")
  val NULL = WaveTokenType("NULL")
  val IDENT = WaveTokenType("IDENT")
  val ATTRIBUTE = WaveTokenType("ATTRIBUTE")

  val NUMBER = WaveTokenType("NUMBER")
  val STRING = WaveTokenType("STRING")
  val CHAR = WaveTokenType("CHAR")

  val LINE_COMMENT = WaveTokenType("LINE_COMMENT")
  val BLOCK_COMMENT = WaveTokenType("BLOCK_COMMENT")

  val OPERATOR = WaveTokenType("OPERATOR")

  val PAREN = WaveTokenType("PAREN")
  val BRACE = WaveTokenType("BRACE")
  val BRACKET = WaveTokenType("BRACKET")

  val COMMA = WaveTokenType("COMMA")
  val DOT = WaveTokenType("DOT")
  val SEMICOLON = WaveTokenType("SEMICOLON")
  val COLON = WaveTokenType("COLON")
}
