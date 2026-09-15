package dev.wavelang.intellij.wave.make

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class MakefileLexer : LexerBase() {
  private var buffer: CharSequence = ""
  private var endOffset: Int = 0
  private var position: Int = 0
  private var tokenStart: Int = 0
  private var tokenEnd: Int = 0
  private var tokenType: IElementType? = null

  private val directives = setOf(
    "include", "-include", "sinclude",
    "ifdef", "ifndef", "ifeq", "ifneq", "else", "endif",
    "define", "endef", "export", "unexport", "override", "private", "vpath"
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

    if (scanCommandLine()) return
    if (scanWhitespace()) return
    if (scanComment()) return
    if (scanVariableReference()) return
    if (scanString()) return
    if (scanNumber()) return
    if (scanDirective()) return
    if (scanTarget()) return
    if (scanIdentifier()) return
    if (scanAssignmentOrOperators()) return
    if (scanPunctuation()) return

    tokenType = TokenType.BAD_CHARACTER
    tokenEnd = position + 1
    position = tokenEnd
  }

  private fun scanCommandLine(): Boolean {
    if (!isLinePrefixWhitespace(position)) return false

    var i = position
    while (i < endOffset && (buffer[i] == ' ' || buffer[i] == '\r')) i++
    if (i >= endOffset || buffer[i] != '\t') return false

    i++
    while (i < endOffset && buffer[i] != '\n') i++
    emit(MakeTokens.COMMAND, i)
    return true
  }

  private fun scanWhitespace(): Boolean {
    if (!buffer[position].isWhitespace()) return false

    var i = position
    if (buffer[i] == '\r') {
      i++
      if (i < endOffset && buffer[i] == '\n') i++
      emit(TokenType.WHITE_SPACE, i)
      return true
    }
    if (buffer[i] == '\n') {
      emit(TokenType.WHITE_SPACE, i + 1)
      return true
    }

    i++
    while (i < endOffset) {
      val ch = buffer[i]
      if (ch == '\n' || ch == '\r' || !ch.isWhitespace()) break
      i++
    }
    emit(TokenType.WHITE_SPACE, i)
    return true
  }

  private fun scanComment(): Boolean {
    if (buffer[position] != '#') return false
    var i = position + 1
    while (i < endOffset && buffer[i] != '\n') i++
    emit(MakeTokens.COMMENT, i)
    return true
  }

  private fun scanVariableReference(): Boolean {
    if (buffer[position] != '$' || position + 1 >= endOffset) return false
    val next = buffer[position + 1]
    if (next == '(' || next == '{') {
      val closing = if (next == '(') ')' else '}'
      var i = position + 2
      while (i < endOffset && buffer[i] != closing && buffer[i] != '\n') i++
      if (i < endOffset && buffer[i] == closing) i++
      emit(MakeTokens.VARIABLE, i)
      return true
    }

    emit(MakeTokens.VARIABLE, (position + 2).coerceAtMost(endOffset))
    return true
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

    emit(MakeTokens.STRING, i.coerceAtMost(endOffset))
    return true
  }

  private fun scanNumber(): Boolean {
    if (!buffer[position].isDigit()) return false
    var i = position + 1
    while (i < endOffset && (buffer[i].isDigit() || buffer[i].lowercaseChar() in 'a'..'f' || buffer[i] == 'x')) i++
    emit(MakeTokens.NUMBER, i)
    return true
  }

  private fun scanDirective(): Boolean {
    if (!isLinePrefixWhitespace(position)) return false
    val c = buffer[position]
    if (!(c.isLetter() || c == '-')) return false

    var i = position + 1
    while (i < endOffset) {
      val ch = buffer[i]
      if (!(ch.isLetterOrDigit() || ch == '_' || ch == '-')) break
      i++
    }

    val text = buffer.subSequence(position, i).toString()
    if (!directives.contains(text)) return false

    emit(MakeTokens.DIRECTIVE, i)
    return true
  }

  private fun scanTarget(): Boolean {
    if (!isLinePrefixWhitespace(position)) return false

    val c = buffer[position]
    if (!(c.isLetterOrDigit() || c == '_' || c == '.' || c == '/')) return false

    var i = position + 1
    while (i < endOffset) {
      val ch = buffer[i]
      if (!(ch.isLetterOrDigit() || ch == '_' || ch == '.' || ch == '/' || ch == '-' || ch == '$' || ch == '(' || ch == ')' || ch == '%')) {
        break
      }
      i++
    }

    if (i == position) return false

    var j = i
    while (j < endOffset && (buffer[j] == ' ' || buffer[j] == '\t' || buffer[j] == '\r')) j++
    if (j >= endOffset || buffer[j] != ':') return false
    if (j + 1 < endOffset && buffer[j + 1] == '=') return false

    emit(MakeTokens.TARGET, i)
    return true
  }

  private fun scanIdentifier(): Boolean {
    val c = buffer[position]
    if (!(c.isLetter() || c == '_' || c == '.' || c == '-')) return false
    var i = position + 1
    while (i < endOffset) {
      val ch = buffer[i]
      if (!(ch.isLetterOrDigit() || ch == '_' || ch == '.' || ch == '-')) break
      i++
    }
    emit(MakeTokens.IDENT, i)
    return true
  }

  private fun scanAssignmentOrOperators(): Boolean {
    fun match(a: Char, b: Char): Boolean {
      return position + 1 < endOffset && buffer[position] == a && buffer[position + 1] == b
    }

    if (match(':', '=') || match('+', '=') || match('?', '=')) {
      emit(MakeTokens.ASSIGN, position + 2)
      return true
    }
    if (buffer[position] == '=') {
      emit(MakeTokens.ASSIGN, position + 1)
      return true
    }
    if (buffer[position] in charArrayOf('+', '-', '*', '/', '%', '$', '@', '^', '<', '>')) {
      emit(MakeTokens.OPERATOR, position + 1)
      return true
    }
    return false
  }

  private fun scanPunctuation(): Boolean {
    return when (buffer[position]) {
      ':' -> {
        emit(MakeTokens.COLON, position + 1)
        true
      }
      ',' -> {
        emit(MakeTokens.COMMA, position + 1)
        true
      }
      '(', ')', '{', '}' -> {
        emit(MakeTokens.PAREN, position + 1)
        true
      }
      else -> false
    }
  }

  private fun emit(type: IElementType, end: Int) {
    tokenType = type
    tokenEnd = end
    position = end
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
}
