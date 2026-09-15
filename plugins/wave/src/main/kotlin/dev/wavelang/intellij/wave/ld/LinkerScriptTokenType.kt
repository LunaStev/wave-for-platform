package dev.wavelang.intellij.wave.ld

import com.intellij.psi.tree.IElementType

open class LinkerScriptTokenType(debugName: String) : IElementType(debugName, LinkerScriptLanguage)

object LinkerScriptTokens {
  val COMMENT = LinkerScriptTokenType("COMMENT")
  val KEYWORD = LinkerScriptTokenType("KEYWORD")
  val BUILTIN = LinkerScriptTokenType("BUILTIN")
  val SECTION = LinkerScriptTokenType("SECTION")
  val NUMBER = LinkerScriptTokenType("NUMBER")
  val STRING = LinkerScriptTokenType("STRING")
  val IDENT = LinkerScriptTokenType("IDENT")
  val OPERATOR = LinkerScriptTokenType("OPERATOR")
  val BRACE = LinkerScriptTokenType("BRACE")
  val PAREN = LinkerScriptTokenType("PAREN")
  val COMMA = LinkerScriptTokenType("COMMA")
  val COLON = LinkerScriptTokenType("COLON")
  val SEMICOLON = LinkerScriptTokenType("SEMICOLON")
}
