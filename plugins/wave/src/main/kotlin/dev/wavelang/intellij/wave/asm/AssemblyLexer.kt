package dev.wavelang.intellij.wave.asm

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class AssemblyLexer : LexerBase() {
  private var buffer: CharSequence = ""
  private var endOffset: Int = 0
  private var position: Int = 0
  private var tokenStart: Int = 0
  private var tokenEnd: Int = 0
  private var tokenType: IElementType? = null

  private val instructions = setOf(
    // x86 / x86-64
    "mov", "lea", "push", "pop", "cmp", "test", "call", "ret", "jmp", "je", "jne", "jg", "jge", "jl", "jle",
    "add", "sub", "imul", "idiv", "and", "or", "xor", "shl", "shr", "nop", "int", "syscall",
    // ARM / AArch64
    "ldr", "str", "ldp", "stp", "b", "bl", "bx", "adr", "adrp", "cbz", "cbnz",
    // RISC-V
    "li", "la", "lw", "sw", "ld", "sd", "addi", "mul", "div", "slli", "srli",
    "beq", "bne", "blt", "bge", "jal", "jalr", "ecall"
  )

  private val fixedRegisters = setOf(
    // x86 / x86-64
    "rax", "rbx", "rcx", "rdx", "rsi", "rdi", "rsp", "rbp",
    "eax", "ebx", "ecx", "edx", "esi", "edi", "esp", "ebp",
    "ax", "bx", "cx", "dx", "si", "di", "sp", "bp",
    "rip", "eip", "cs", "ds", "es", "fs", "gs", "ss",
    // ARM / AArch64
    "sp", "lr", "pc", "fp",
    // RISC-V aliases
    "zero", "ra", "gp", "tp",
    "t0", "t1", "t2", "t3", "t4", "t5", "t6",
    "s0", "s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8", "s9", "s10", "s11",
    "a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7"
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
    if (scanDirective()) return
    if (scanLabel()) return
    if (scanNumber()) return
    if (scanWordLike()) return
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
    if (buffer[position] == ';') {
      var i = position + 1
      while (i < endOffset && buffer[i] != '\n') i++
      emit(AssemblyTokens.COMMENT, i)
      return true
    }

    if (position + 1 < endOffset && buffer[position] == '/' && buffer[position + 1] == '/') {
      var i = position + 2
      while (i < endOffset && buffer[i] != '\n') i++
      emit(AssemblyTokens.COMMENT, i)
      return true
    }

    if (buffer[position] == '#' && isLinePrefixWhitespace(position)) {
      var i = position + 1
      while (i < endOffset && buffer[i] != '\n') i++
      emit(AssemblyTokens.COMMENT, i)
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
    emit(AssemblyTokens.STRING, i.coerceAtMost(endOffset))
    return true
  }

  private fun scanDirective(): Boolean {
    if (buffer[position] != '.') return false
    if (position + 1 >= endOffset || !isIdentifierPart(buffer[position + 1])) return false

    var i = position + 2
    while (i < endOffset && isIdentifierPart(buffer[i])) i++
    emit(AssemblyTokens.DIRECTIVE, i)
    return true
  }

  private fun scanLabel(): Boolean {
    if (!isLinePrefixWhitespace(position)) return false
    val c = buffer[position]
    if (!(c.isLetter() || c == '_' || c == '.')) return false

    var i = position + 1
    while (i < endOffset && isIdentifierPart(buffer[i])) i++

    if (i < endOffset && buffer[i] == ':') {
      emit(AssemblyTokens.LABEL, i)
      return true
    }
    return false
  }

  private fun scanNumber(): Boolean {
    if (!buffer[position].isDigit()) return false
    var i = position + 1

    if (buffer[position] == '0' && i < endOffset && (buffer[i] == 'x' || buffer[i] == 'X')) {
      i++
      while (i < endOffset && (buffer[i].isDigit() || buffer[i].lowercaseChar() in 'a'..'f')) i++
      emit(AssemblyTokens.NUMBER, i)
      return true
    }

    while (i < endOffset && (buffer[i].isDigit() || buffer[i].isLetter())) i++
    emit(AssemblyTokens.NUMBER, i)
    return true
  }

  private fun scanWordLike(): Boolean {
    val c = buffer[position]
    if (c == '%' && position + 1 < endOffset && isIdentifierPart(buffer[position + 1])) {
      var i = position + 2
      while (i < endOffset && isIdentifierPart(buffer[i])) i++
      val name = buffer.subSequence(position + 1, i).toString().lowercase()
      if (isRegister(name)) {
        emit(AssemblyTokens.REGISTER, i)
        return true
      }
    }

    if (!(c.isLetter() || c == '_' || c == '.')) return false
    var i = position + 1
    while (i < endOffset && isIdentifierPart(buffer[i])) i++

    val text = buffer.subSequence(position, i).toString().lowercase()
    val type = when {
      instructions.contains(text) -> AssemblyTokens.INSTRUCTION
      isRegister(text) -> AssemblyTokens.REGISTER
      else -> AssemblyTokens.IDENT
    }
    emit(type, i)
    return true
  }

  private fun scanPunctuationOrOperator(): Boolean {
    return when (buffer[position]) {
      ',' -> {
        emit(AssemblyTokens.COMMA, position + 1)
        true
      }
      ':' -> {
        emit(AssemblyTokens.COLON, position + 1)
        true
      }
      '(', ')', '[', ']' -> {
        emit(AssemblyTokens.BRACKET, position + 1)
        true
      }
      '+', '-', '*', '/', '%', '$', '#', '@', '!', '&', '|', '^', '<', '>', '=' -> {
        emit(AssemblyTokens.OPERATOR, position + 1)
        true
      }
      else -> false
    }
  }

  private fun isRegister(name: String): Boolean {
    if (fixedRegisters.contains(name)) return true
    if (name.matches(Regex("r(1[0-5]|[0-9])"))) return true
    if (name.matches(Regex("r([8-9]|1[0-5])d?"))) return true
    if (name.matches(Regex("x([0-9]|[12][0-9]|3[01])"))) return true
    if (name.matches(Regex("w([0-9]|[12][0-9]|3[01])"))) return true
    return false
  }

  private fun isIdentifierPart(ch: Char): Boolean {
    return ch.isLetterOrDigit() || ch == '_' || ch == '.' || ch == '$'
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
