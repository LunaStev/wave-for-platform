package dev.wavelang.intellij.wave.highlight

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class WaveLexer : LexerBase() {
  private var buffer: CharSequence = ""
  private var endOffset: Int = 0

  private var position: Int = 0
  private var tokenStart: Int = 0
  private var tokenEnd: Int = 0
  private var tokenType: IElementType? = null

  private val keywords = setOf(
    "fun", "extern", "export", "pub", "type", "enum", "variant", "static", "var", "deref", "const",
    "if", "else", "proto", "struct", "while", "for", "module", "class",
    "in", "out", "clobber", "is", "as", "asm", "xnand", "import", "return",
    "continue", "print", "input", "println", "match", "break",
  )

  private val removedDeclarationKeywords = setOf("let", "mut")

  private val builtinTypeKeywords = setOf(
    "void", "bool", "char", "byte", "str"
  )

  private val typeKeywords = setOf(
    "i8", "i16", "i32", "i64", "i128", "i256", "i512", "i1024", "isz",
    "u8", "u16", "u32", "u64", "u128", "u256", "u512", "u1024", "usz",
    "f32", "f64", "ptr", "array"
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
    if (scanLineComment()) return
    if (scanBlockCommentNested()) return
    if (scanAttribute()) return
    if (scanString()) return
    if (scanChar()) return
    if (scanNumber()) return
    if (scanIdentifierOrKeyword()) return
    if (scanOperatorOrPunctuation()) return

    tokenType = TokenType.BAD_CHARACTER
    tokenEnd = position + 1
    position = tokenEnd
  }

  private fun scanWhitespace(): Boolean {
    if (!buffer[position].isWhitespace()) return false

    var i = position + 1
    while (i < endOffset && buffer[i].isWhitespace()) i++

    tokenType = TokenType.WHITE_SPACE
    tokenEnd = i
    position = i
    return true
  }

  private fun scanLineComment(): Boolean {
    if (position + 1 >= endOffset) return false
    if (buffer[position] != '/' || buffer[position + 1] != '/') return false

    var i = position + 2
    while (i < endOffset && buffer[i] != '\n') i++

    tokenType = WaveTokens.LINE_COMMENT
    tokenEnd = i
    position = i
    return true
  }

  private fun scanBlockCommentNested(): Boolean {
    if (position + 1 >= endOffset) return false
    if (buffer[position] != '/' || buffer[position + 1] != '*') return false

    var i = position + 2
    var depth = 1
    while (i < endOffset) {
      if (i + 1 < endOffset && buffer[i] == '/' && buffer[i + 1] == '*') {
        depth++
        i += 2
        continue
      }
      if (i + 1 < endOffset && buffer[i] == '*' && buffer[i + 1] == '/') {
        depth--
        i += 2
        if (depth == 0) break
        continue
      }
      i++
    }

    tokenType = WaveTokens.BLOCK_COMMENT
    tokenEnd = i.coerceAtMost(endOffset)
    position = tokenEnd
    return true
  }

  private fun scanString(): Boolean {
    if (buffer[position] != '"') return false

    var i = position + 1
    while (i < endOffset) {
      val ch = buffer[i]
      if (ch == '\\') {
        i += if (i + 1 < endOffset) 2 else 1
        continue
      }
      if (ch == '"') {
        i++
        break
      }
      if (ch == '\n') break
      i++
    }

    tokenType = WaveTokens.STRING
    tokenEnd = i.coerceAtMost(endOffset)
    position = tokenEnd
    return true
  }

  private fun scanAttribute(): Boolean {
    if (position + 1 >= endOffset) return false
    if (buffer[position] != '#' || buffer[position + 1] != '[') return false

    var i = position + 2
    var bracketDepth = 1
    var inString = false
    var inChar = false
    var escaped = false

    while (i < endOffset) {
      val ch = buffer[i]

      if (inString) {
        if (escaped) {
          escaped = false
        }
        else if (ch == '\\') {
          escaped = true
        }
        else if (ch == '"') {
          inString = false
        }
        i++
        continue
      }

      if (inChar) {
        if (escaped) {
          escaped = false
        }
        else if (ch == '\\') {
          escaped = true
        }
        else if (ch == '\'') {
          inChar = false
        }
        i++
        continue
      }

      when (ch) {
        '"' -> {
          inString = true
          i++
        }
        '\'' -> {
          inChar = true
          i++
        }
        '[' -> {
          bracketDepth++
          i++
        }
        ']' -> {
          bracketDepth--
          i++
          if (bracketDepth == 0) break
        }
        '\n' -> break
        else -> i++
      }
    }

    tokenType = WaveTokens.ATTRIBUTE
    tokenEnd = i.coerceAtMost(endOffset)
    position = tokenEnd
    return true
  }

  private fun scanChar(): Boolean {
    if (buffer[position] != '\'') return false

    var i = position + 1
    if (i < endOffset) {
      if (buffer[i] == '\\') {
        i += if (i + 1 < endOffset) 2 else 1
        if (i <= endOffset && i < endOffset && buffer[i] == '\'') i++
      }
      else {
        i++
        if (i < endOffset && buffer[i] == '\'') i++
      }
    }

    tokenType = WaveTokens.CHAR
    tokenEnd = i.coerceAtMost(endOffset)
    position = tokenEnd
    return true
  }

  private fun scanNumber(): Boolean {
    val first = buffer[position]
    if (!first.isDigit()) return false

    var i = position

    if (first == '0' && i + 1 < endOffset && (buffer[i + 1] == 'b' || buffer[i + 1] == 'B')) {
      i += 2
      while (i < endOffset && (buffer[i] == '0' || buffer[i] == '1')) i++
      tokenType = WaveTokens.NUMBER
      tokenEnd = i
      position = i
      return true
    }

    if (first == '0' && i + 1 < endOffset && (buffer[i + 1] == 'x' || buffer[i + 1] == 'X')) {
      i += 2
      while (i < endOffset && (buffer[i].isDigit() || buffer[i].lowercaseChar() in 'a'..'f')) i++
      tokenType = WaveTokens.NUMBER
      tokenEnd = i
      position = i
      return true
    }

    i++
    while (i < endOffset && buffer[i].isDigit()) i++

    if (i < endOffset && buffer[i] == '.') {
      i++
      while (i < endOffset && buffer[i].isDigit()) i++
    }

    tokenType = WaveTokens.NUMBER
    tokenEnd = i
    position = i
    return true
  }

  private fun scanIdentifierOrKeyword(): Boolean {
    val c = buffer[position]
    if (!(c.isLetter() || c == '_')) return false

    var i = position + 1
    while (i < endOffset) {
      val ch = buffer[i]
      if (!(ch.isLetterOrDigit() || ch == '_')) break
      i++
    }

    val text = buffer.subSequence(position, i).toString()
    tokenType = when {
      text == "true" || text == "false" -> WaveTokens.BOOL
      text == "null" -> WaveTokens.NULL
      builtinTypeKeywords.contains(text) -> WaveTokens.TYPE
      typeKeywords.contains(text) -> WaveTokens.TYPE
      removedDeclarationKeywords.contains(text) -> WaveTokens.DEPRECATED
      keywords.contains(text) -> WaveTokens.KEYWORD
      else -> WaveTokens.IDENT
    }

    tokenEnd = i
    position = i
    return true
  }

  private fun scanOperatorOrPunctuation(): Boolean {
    fun match2(a: Char, b: Char): Boolean {
      return position + 1 < endOffset && buffer[position] == a && buffer[position + 1] == b
    }

    fun emit(type: IElementType, len: Int): Boolean {
      tokenType = type
      tokenEnd = position + len
      position = tokenEnd
      return true
    }

    if (match2('+', '+') || match2('+', '=') || match2('-', '-') || match2('-', '>') ||
      match2('-', '=') || match2('*', '=') || match2('/', '=') || match2('%', '=') ||
      match2('<', '<') || match2('<', '=') || match2('>', '>') || match2('>', '=') ||
      match2('=', '=') || match2('&', '&') || match2('|', '|') || match2('!', '=') ||
      match2('!', '&') || match2('!', '|') || match2('~', '^') || match2('?', '?')
    ) {
      return emit(WaveTokens.OPERATOR, 2)
    }

    return when (val ch = buffer[position]) {
      '+', '-', '*', '/', '%', '=', '<', '>', '&', '|', '!', '^', '~', '?' -> emit(WaveTokens.OPERATOR, 1)
      '#' -> emit(WaveTokens.OPERATOR, 1)
      '(' , ')' -> emit(WaveTokens.PAREN, 1)
      '{' , '}' -> emit(WaveTokens.BRACE, 1)
      '[' , ']' -> emit(WaveTokens.BRACKET, 1)
      ',' -> emit(WaveTokens.COMMA, 1)
      '.' -> emit(WaveTokens.DOT, 1)
      ';' -> emit(WaveTokens.SEMICOLON, 1)
      ':' -> emit(WaveTokens.COLON, 1)
      else -> false
    }
  }
}
