package dev.wavelang.intellij.wave.make

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class MakefileSyntaxHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer = MakefileLexer()

  override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = when (tokenType) {
    MakeTokens.COMMENT -> pack(COMMENT)
    MakeTokens.DIRECTIVE -> pack(DIRECTIVE)
    MakeTokens.VARIABLE -> pack(VARIABLE)
    MakeTokens.TARGET -> pack(TARGET)
    MakeTokens.COMMAND -> pack(COMMAND)
    MakeTokens.ASSIGN, MakeTokens.OPERATOR -> pack(OPERATOR)
    MakeTokens.STRING -> pack(STRING)
    MakeTokens.NUMBER -> pack(NUMBER)
    MakeTokens.COLON -> pack(COLON)
    MakeTokens.COMMA -> pack(COMMA)
    MakeTokens.PAREN -> pack(PAREN)
    com.intellij.psi.TokenType.BAD_CHARACTER -> pack(BAD_CHAR)
    else -> emptyArray()
  }

  companion object {
    val COMMENT: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("MAKE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val DIRECTIVE: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("MAKE_DIRECTIVE", DefaultLanguageHighlighterColors.KEYWORD)
    val VARIABLE: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("MAKE_VARIABLE", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
    val TARGET: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("MAKE_TARGET", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)
    val COMMAND: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("MAKE_COMMAND", DefaultLanguageHighlighterColors.STRING)
    val OPERATOR: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("MAKE_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val STRING: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("MAKE_STRING", DefaultLanguageHighlighterColors.STRING)
    val NUMBER: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("MAKE_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    val COLON: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("MAKE_COLON", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val COMMA: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("MAKE_COMMA", DefaultLanguageHighlighterColors.COMMA)
    val PAREN: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("MAKE_PAREN", DefaultLanguageHighlighterColors.PARENTHESES)
    val BAD_CHAR: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("MAKE_BAD_CHAR", HighlighterColors.BAD_CHARACTER)
  }
}
