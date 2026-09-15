package dev.wavelang.intellij.wave.ld

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class LinkerScriptSyntaxHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer = LinkerScriptLexer()

  override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = when (tokenType) {
    LinkerScriptTokens.COMMENT -> pack(COMMENT)
    LinkerScriptTokens.KEYWORD -> pack(KEYWORD)
    LinkerScriptTokens.BUILTIN -> pack(BUILTIN)
    LinkerScriptTokens.SECTION -> pack(SECTION)
    LinkerScriptTokens.NUMBER -> pack(NUMBER)
    LinkerScriptTokens.STRING -> pack(STRING)
    LinkerScriptTokens.OPERATOR -> pack(OPERATOR)
    LinkerScriptTokens.BRACE -> pack(BRACE)
    LinkerScriptTokens.PAREN -> pack(PAREN)
    LinkerScriptTokens.COMMA -> pack(COMMA)
    LinkerScriptTokens.COLON -> pack(COLON)
    LinkerScriptTokens.SEMICOLON -> pack(SEMICOLON)
    com.intellij.psi.TokenType.BAD_CHARACTER -> pack(BAD_CHAR)
    else -> emptyArray()
  }

  companion object {
    val COMMENT: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("LD_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val KEYWORD: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("LD_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
    val BUILTIN: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("LD_BUILTIN", DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL)
    val SECTION: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("LD_SECTION", DefaultLanguageHighlighterColors.FUNCTION_DECLARATION)
    val NUMBER: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("LD_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    val STRING: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("LD_STRING", DefaultLanguageHighlighterColors.STRING)
    val OPERATOR: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("LD_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val BRACE: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("LD_BRACE", DefaultLanguageHighlighterColors.BRACES)
    val PAREN: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("LD_PAREN", DefaultLanguageHighlighterColors.PARENTHESES)
    val COMMA: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("LD_COMMA", DefaultLanguageHighlighterColors.COMMA)
    val COLON: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("LD_COLON", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val SEMICOLON: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("LD_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON)
    val BAD_CHAR: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("LD_BAD_CHAR", HighlighterColors.BAD_CHARACTER)
  }
}
