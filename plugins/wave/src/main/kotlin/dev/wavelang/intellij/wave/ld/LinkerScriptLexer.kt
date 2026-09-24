package dev.wavelang.intellij.wave.ld

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class LinkerScriptLexer : LexerBase() {
  private var buffer: CharSequence = ""
  private var endOffset: Int = 0
  private var position: Int = 0
  private var tokenStart: Int = 0
  private var tokenEnd: Int = 0
  private var tokenType: IElementType? = null

  private val keywords = setOf(
    "ENTRY", "MEMORY", "SECTIONS", "PHDRS",
    "OUTPUT", "OUTPUT_FORMAT", "OUTPUT_ARCH", "SEARCH_DIR",
    "INPUT", "GROUP", "EXTERN", "STARTUP", "TARGET", "INCLUDE",
    "PROVIDE", "PROVIDE_HIDDEN", "KEEP",
    "SORT", "SORT_BY_NAME", "SORT_BY_ALIGNMENT", "SORT_BY_INIT_PRIORITY", "SORT_BY_ADDRESS",
    "ONLY_IF_RO", "ONLY_IF_RW", "FILL", "AT", "SUBALIGN",
    "INSERT", "AFTER", "BEFORE", "NOCROSSREFS", "NOCROSSREFS_TO"
  )

  private val builtins = setOf(
    "ALIGN", "ALIGNOF", "ADDR", "LOADADDR", "SIZEOF", "SIZEOF_HEADERS", "BLOCK",
    "ORIGIN", "LENGTH", "ASSERT", "DEFINED", "ABSOLUTE", "MAX", "MIN", "NEXT"
  )

  override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
    this.buffer = buffer
    this.position = startOffset
    this.endOffset = endOffset
    this.tokenStart = startOffset
    this.tokenEnd = startOffset
    this.tokenType = null
    advance()
  }

  override fun getState(): Int = 0
  override fun getTokenStart(): Int = tokenStart
  override fun getTokenEnd(): Int = tokenEnd
  override fun getTokenType(): IElementType? = tokenType
  override fun getBufferSequence(): CharSequence = buffer
  override fun getBufferEnd(): Int = endOffset

  override fun advance() {
    if (position >= endOffset) {
      tokenType = null
      return
    }

    tokenStart = position

    if (scanWhitespace()) return
    if (scanComment()) return
    if (scanString()) return
    if (scanSection()) return
    if (scanNumber()) return
    if (scanWord()) return
    if (scanPunctuationOrOperator()) return

    tokenType = TokenType.BAD_CHARACTER
    tokenEnd = position + 1
    position = tokenEnd
  }

  private fun scanWhitespace(): Boolean {
    if (!buffer[position].isWhitespace()) return false

    var i = position + 1
    while (i < endOffset && buffer[i].isWhitespace()) i++
    emit(TokenType.WHITE_SPACE, i)
    return true
  }

  private fun scanComment(): Boolean {
    if (position + 1 < endOffset && buffer[position] == '/' && buffer[position + 1] == '/') {
      var i = position + 2
      while (i < endOffset && buffer[i] != '\n') i++
      emit(LinkerScriptTokens.COMMENT, i)
      return true
    }

    if (position + 1 < endOffset && buffer[position] == '/' && buffer[position + 1] == '*') {
      var i = position + 2
      while (i + 1 < endOffset && !(buffer[i] == '*' && buffer[i + 1] == '/')) i++
      if (i + 1 < endOffset) i += 2 else i = endOffset
      emit(LinkerScriptTokens.COMMENT, i)
      return true
    }

    if (buffer[position] == '#' && isLinePrefixWhitespace(position)) {
      var i = position + 1
      while (i < endOffset && buffer[i] != '\n') i++
      emit(LinkerScriptTokens.COMMENT, i)
      return true
    }

    return false
  }

  private fun scanString(): Boolean {
    val quote = buffer[position]
    if (quote != '"' && quote != '\'') return false

    var i = position + 1
    while (i < endOffset) {
      val ch = buffer[i]
      if (ch == '\\') {
        i += if (i + 1 < endOffset) 2 else 1
        continue
      }
      if (ch == quote) {
        i++
        break
      }
      if (ch == '\n') break
      i++
    }

    emit(LinkerScriptTokens.STRING, i.coerceAtMost(endOffset))
    return true
  }

  private fun scanSection(): Boolean {
    if (buffer[position] != '.') return false
    if (position + 1 >= endOffset || !isIdentifierPart(buffer[position + 1])) return false

    var i = position + 2
    while (i < endOffset && isIdentifierPart(buffer[i])) i++
    emit(LinkerScriptTokens.SECTION, i)
    return true
  }

  private fun scanNumber(): Boolean {
    if (!buffer[position].isDigit()) return false

    var i = position + 1
    if (buffer[position] == '0' && i < endOffset && (buffer[i] == 'x' || buffer[i] == 'X')) {
      i++
      while (i < endOffset && (buffer[i].isDigit() || buffer[i].lowercaseChar() in 'a'..'f')) i++
      if (i < endOffset && (buffer[i] == 'K' || buffer[i] == 'k' || buffer[i] == 'M' || buffer[i] == 'm' || buffer[i] == 'G' || buffer[i] == 'g')) i++
      emit(LinkerScriptTokens.NUMBER, i)
      return true
    }

    while (i < endOffset && buffer[i].isDigit()) i++
    if (i < endOffset && (buffer[i] == 'K' || buffer[i] == 'k' || buffer[i] == 'M' || buffer[i] == 'm' || buffer[i] == 'G' || buffer[i] == 'g')) i++

    emit(LinkerScriptTokens.NUMBER, i)
    return true
  }

  private fun scanWord(): Boolean {
    val c = buffer[position]
    if (!(c.isLetter() || c == '_')) return false

    var i = position + 1
    while (i < endOffset && isIdentifierPart(buffer[i])) i++

    val text = buffer.subSequence(position, i).toString()
    val type = when {
      keywords.contains(text.uppercase()) -> LinkerScriptTokens.KEYWORD
      builtins.contains(text.uppercase()) -> LinkerScriptTokens.BUILTIN
      else -> LinkerScriptTokens.IDENT
    }

    emit(type, i)
    return true
  }

  private fun scanPunctuationOrOperator(): Boolean {
    return when (buffer[position]) {
      '{', '}' -> {
        emit(LinkerScriptTokens.BRACE, position + 1)
        true
      }
      '(', ')', '[', ']' -> {
        emit(LinkerScriptTokens.PAREN, position + 1)
        true
      }
      ',' -> {
        emit(LinkerScriptTokens.COMMA, position + 1)
        true
      }
      ':' -> {
        emit(LinkerScriptTokens.COLON, position + 1)
        true
      }
      ';' -> {
        emit(LinkerScriptTokens.SEMICOLON, position + 1)
        true
      }
      '.', '=', '+', '-', '*', '/', '%', '&', '|', '^', '~', '<', '>', '!', '?', '@' -> {
        emit(LinkerScriptTokens.OPERATOR, position + 1)
        true
      }
      else -> false
    }
  }

  private fun isIdentifierPart(ch: Char): Boolean {
    return ch.isLetterOrDigit() || ch == '_' || ch == '.' || ch == '$' || ch == '-'
  }

  private fun isLinePrefixWhitespace(pos: Int): Boolean {
    var i = pos - 1
    while (i >= 0 && buffer[i] != '\n') {
      val ch = buffer[i]
      if (ch != ' ' && ch != '\t' && ch != '\r') return false
      i--
    }
    return true
  }

  private fun emit(type: IElementType, end: Int) {
    tokenType = type
    tokenEnd = end
    position = end
  }
}
