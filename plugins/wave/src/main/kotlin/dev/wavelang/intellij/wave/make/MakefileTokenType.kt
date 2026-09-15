package dev.wavelang.intellij.wave.make

import com.intellij.psi.tree.IElementType

open class MakefileTokenType(debugName: String) : IElementType(debugName, MakefileLanguage)

object MakeTokens {
  val COMMENT = MakefileTokenType("COMMENT")
  val DIRECTIVE = MakefileTokenType("DIRECTIVE")
  val VARIABLE = MakefileTokenType("VARIABLE")
  val TARGET = MakefileTokenType("TARGET")
  val COMMAND = MakefileTokenType("COMMAND")
  val ASSIGN = MakefileTokenType("ASSIGN")
  val STRING = MakefileTokenType("STRING")
  val NUMBER = MakefileTokenType("NUMBER")
  val IDENT = MakefileTokenType("IDENT")
  val OPERATOR = MakefileTokenType("OPERATOR")
  val COLON = MakefileTokenType("COLON")
  val COMMA = MakefileTokenType("COMMA")
  val PAREN = MakefileTokenType("PAREN")
}
