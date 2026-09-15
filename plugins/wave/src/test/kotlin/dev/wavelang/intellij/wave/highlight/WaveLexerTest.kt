package dev.wavelang.intellij.wave.highlight

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WaveLexerTest {

  private data class LexedToken(val type: IElementType, val text: String)

  private fun lexAll(source: String): List<LexedToken> {
    val lexer = WaveLexer()
    lexer.start(source, 0, source.length, 0)

    val out = mutableListOf<LexedToken>()
    while (true) {
      val type = lexer.tokenType ?: break
      val text = source.substring(lexer.tokenStart, lexer.tokenEnd)
      out += LexedToken(type, text)
      lexer.advance()
    }
    return out
  }

  @Test
  fun attributeLineIsLexedAsMetadataToken() {
    val source = "#[target(os=\"linux\")]\nfun main() {}"
    val tokens = lexAll(source)

    assertEquals(WaveTokens.ATTRIBUTE, tokens.first().type)
    assertEquals("#[target(os=\"linux\")]", tokens.first().text)
  }

  @Test
  fun builtinAndContextualTypesAreHighlightedAsType() {
    val source = "var a: bool = true; var b: void = null; var p: ptr<i32>; var arr: array<u8, 4>;"
    val tokens = lexAll(source)
      .filter { it.type != TokenType.WHITE_SPACE }

    val byText = tokens.groupBy { it.text }

    assertTrue(byText["bool"]?.any { it.type == WaveTokens.TYPE } == true)
    assertTrue(byText["void"]?.any { it.type == WaveTokens.TYPE } == true)
    assertTrue(byText["ptr"]?.any { it.type == WaveTokens.TYPE } == true)
    assertTrue(byText["array"]?.any { it.type == WaveTokens.TYPE } == true)
    assertTrue(byText["true"]?.any { it.type == WaveTokens.BOOL } == true)
    assertTrue(byText["null"]?.any { it.type == WaveTokens.NULL } == true)
  }

  @Test
  fun trailingDotFloatIsSingleNumberToken() {
    val source = "var x: f64 = 1.;"
    val tokens = lexAll(source)
      .filter { it.type != TokenType.WHITE_SPACE }

    assertTrue(tokens.any { it.text == "1." && it.type == WaveTokens.NUMBER })
    assertFalse(tokens.any { it.text == "." && it.type == WaveTokens.DOT })
  }

  @Test
  fun importKeepsKeywordAndPathStringTokens() {
    val tokens = lexAll("import(\"std::io::format\");")
      .filter { it.type != TokenType.WHITE_SPACE }

    assertEquals(WaveTokens.KEYWORD, tokens.first { it.text == "import" }.type)
    assertEquals(WaveTokens.STRING, tokens.first { it.text == "\"std::io::format\"" }.type)
  }

  @Test
  fun currentModuleKeywordsAndRemovedDeclarationsAreDistinct() {
    val tokens = lexAll("pub variant Result<T, E> { Ok(T), Err(E) } let mut")
      .filter { it.type != TokenType.WHITE_SPACE }

    assertEquals(WaveTokens.KEYWORD, tokens.first { it.text == "pub" }.type)
    assertEquals(WaveTokens.KEYWORD, tokens.first { it.text == "variant" }.type)
    assertEquals(WaveTokens.DEPRECATED, tokens.first { it.text == "let" }.type)
    assertEquals(WaveTokens.DEPRECATED, tokens.first { it.text == "mut" }.type)
  }

  @Test
  fun selectivePublicImportAndSystemAbiHaveNoBadCharacters() {
    val source = "pub import(\"std::sys::windows::fs\")::{open, close,}; extern(system, \"CreateFileW\") fun open();"
    val tokens = lexAll(source)
    assertFalse(tokens.any { it.type == TokenType.BAD_CHARACTER })
  }
}
