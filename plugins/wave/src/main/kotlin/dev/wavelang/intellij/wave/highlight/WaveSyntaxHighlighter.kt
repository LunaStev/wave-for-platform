package dev.wavelang.intellij.wave.highlight

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class WaveSyntaxHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer(): Lexer = WaveLexer()

  override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> = when (tokenType) {
    WaveTokens.KEYWORD -> pack(KEYWORD)
    WaveTokens.DEPRECATED -> pack(DEPRECATED)
    WaveTokens.TYPE -> pack(TYPE)
    WaveTokens.BOOL, WaveTokens.NULL -> pack(LITERAL_KEYWORD)
    WaveTokens.IDENT -> pack(IDENT)
    WaveTokens.ATTRIBUTE -> pack(ATTRIBUTE)

    WaveTokens.NUMBER -> pack(NUMBER)
    WaveTokens.STRING -> pack(STRING)
    WaveTokens.CHAR -> pack(CHAR)

    WaveTokens.LINE_COMMENT, WaveTokens.BLOCK_COMMENT -> pack(COMMENT)

    WaveTokens.OPERATOR -> pack(OPERATOR)
    WaveTokens.PAREN -> pack(PAREN)
    WaveTokens.BRACE -> pack(BRACE)
    WaveTokens.BRACKET -> pack(BRACKET)

    WaveTokens.COMMA -> pack(COMMA)
    WaveTokens.DOT -> pack(DOT)
    WaveTokens.SEMICOLON -> pack(SEMICOLON)
    WaveTokens.COLON -> pack(COLON)

    com.intellij.psi.TokenType.BAD_CHARACTER -> pack(BAD_CHAR)
    else -> emptyArray()
  }

  companion object {
    val KEYWORD: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("WAVE_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
    val DEPRECATED: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("WAVE_DEPRECATED", HighlighterColors.BAD_CHARACTER)
    val TYPE: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("WAVE_TYPE", DefaultLanguageHighlighterColors.CLASS_NAME)
    val LITERAL_KEYWORD: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("WAVE_LITERAL_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
    val IDENT: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("WAVE_IDENT", DefaultLanguageHighlighterColors.IDENTIFIER)
    val ATTRIBUTE: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("WAVE_ATTRIBUTE", DefaultLanguageHighlighterColors.METADATA)

    val NUMBER: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("WAVE_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    val STRING: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("WAVE_STRING", DefaultLanguageHighlighterColors.STRING)
    val CHAR: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("WAVE_CHAR", DefaultLanguageHighlighterColors.STRING)

    val COMMENT: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("WAVE_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)

    val OPERATOR: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("WAVE_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val PAREN: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("WAVE_PAREN", DefaultLanguageHighlighterColors.PARENTHESES)
    val BRACE: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("WAVE_BRACE", DefaultLanguageHighlighterColors.BRACES)
    val BRACKET: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("WAVE_BRACKET", DefaultLanguageHighlighterColors.BRACKETS)

    val COMMA: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("WAVE_COMMA", DefaultLanguageHighlighterColors.COMMA)
    val DOT: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("WAVE_DOT", DefaultLanguageHighlighterColors.DOT)
    val SEMICOLON: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("WAVE_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON)
    val COLON: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("WAVE_COLON", DefaultLanguageHighlighterColors.OPERATION_SIGN)

    val BAD_CHAR: TextAttributesKey =
      TextAttributesKey.createTextAttributesKey("WAVE_BAD_CHAR", HighlighterColors.BAD_CHARACTER)
  }
}
