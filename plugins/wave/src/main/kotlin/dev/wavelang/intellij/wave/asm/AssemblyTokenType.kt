package dev.wavelang.intellij.wave.asm

import com.intellij.psi.tree.IElementType

open class AssemblyTokenType(debugName: String) : IElementType(debugName, AssemblyLanguage)

object AssemblyTokens {
  val COMMENT = AssemblyTokenType("COMMENT")
  val DIRECTIVE = AssemblyTokenType("DIRECTIVE")
  val LABEL = AssemblyTokenType("LABEL")
  val INSTRUCTION = AssemblyTokenType("INSTRUCTION")
  val REGISTER = AssemblyTokenType("REGISTER")
  val NUMBER = AssemblyTokenType("NUMBER")
  val STRING = AssemblyTokenType("STRING")
  val IDENT = AssemblyTokenType("IDENT")
  val OPERATOR = AssemblyTokenType("OPERATOR")
  val COMMA = AssemblyTokenType("COMMA")
  val COLON = AssemblyTokenType("COLON")
  val BRACKET = AssemblyTokenType("BRACKET")
}
