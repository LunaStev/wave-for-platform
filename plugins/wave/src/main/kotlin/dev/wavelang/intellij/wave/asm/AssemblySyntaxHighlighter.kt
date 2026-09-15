package dev.wavelang.intellij.wave.asm

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class AssemblySyntaxHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer = AssemblyLexer()

  override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = when (tokenType) {
    AssemblyTokens.COMMENT -> pack(COMMENT)
    AssemblyTokens.DIRECTIVE -> pack(DIRECTIVE)
    AssemblyTokens.LABEL -> pack(LABEL)
    AssemblyTokens.INSTRUCTION -> pack(INSTRUCTION)
    AssemblyTokens.REGISTER -> pack(REGISTER)
    AssemblyTokens.NUMBER -> pack(NUMBER)
    AssemblyTokens.STRING -> pack(STRING)
    AssemblyTokens.OPERATOR -> pack(OPERATOR)
    AssemblyTokens.COMMA -> pack(COMMA)
    AssemblyTokens.COLON -> pack(COLON)
    AssemblyTokens.BRACKET -> pack(BRACKET)
    com.intellij.psi.TokenType.BAD_CHARACTER -> pack(BAD_CHAR)
    else -> emptyArray()
  }

  companion object {
    val COMMENT: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("ASM_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val DIRECTIVE: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("ASM_DIRECTIVE", DefaultLanguageHighlighterColors.KEYWORD)
    val LABEL: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("ASM_LABEL", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)
    val INSTRUCTION: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("ASM_INSTRUCTION", DefaultLanguageHighlighterColors.KEYWORD)
    val REGISTER: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("ASM_REGISTER", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
    val NUMBER: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("ASM_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    val STRING: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("ASM_STRING", DefaultLanguageHighlighterColors.STRING)
    val OPERATOR: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("ASM_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val COMMA: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("ASM_COMMA", DefaultLanguageHighlighterColors.COMMA)
    val COLON: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("ASM_COLON", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val BRACKET: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("ASM_BRACKET", DefaultLanguageHighlighterColors.BRACKETS)
    val BAD_CHAR: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("ASM_BAD_CHAR", HighlighterColors.BAD_CHARACTER)
  }
}
