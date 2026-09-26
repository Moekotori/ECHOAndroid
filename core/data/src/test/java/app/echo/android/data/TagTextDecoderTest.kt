package app.echo.android.data

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagTextDecoderTest {
    @Test
    fun unicodeLanguagesAndSupplementaryCharactersRoundTrip() {
        val titles = listOf("六兆年と一夜物語", "繁體中文", "봄날", "Привет", "مرحبا", "नमस्ते", "Hélène", "𠮷野家 🎵")
        for (text in titles) {
            for (charset in listOf(Charsets.UTF_8, Charsets.UTF_16, Charsets.UTF_16BE)) {
                assertEquals("$text / $charset", text, TagTextDecoder.decodeDeclared(text.toByteArray(charset), charset))
            }
            assertEquals(text, TagTextDecoder.decode(text.toByteArray(Charsets.UTF_8)))
        }
    }

    @Test
    fun utf16PaddingDoesNotRemoveHalfOfTheLastCharacter() {
        for (charset in listOf(Charsets.UTF_16LE, Charsets.UTF_16BE)) {
            val bom = if (charset == Charsets.UTF_16LE) byteArrayOf(-1, -2) else byteArrayOf(-2, -1)
            for (title in listOf("AB", "Song A", "中文A", "Ā")) {
                for (padding in listOf(byteArrayOf(), byteArrayOf(0, 0))) {
                    assertEquals(title, TagTextDecoder.decode(bom + title.toByteArray(charset) + padding))
                }
            }
            assertEquals("Song A", TagTextDecoder.decode("Song A".toByteArray(charset)))
        }
    }

    @Test
    fun shiftJisKanaDisambiguatesJapaneseFromGbk() {
        for (title in listOf("六兆年と一夜物語", "夜に駆ける", "カタオモイ")) {
            assertEquals(title, TagTextDecoder.decode(title.toByteArray(Charset.forName("Shift_JIS"))))
        }
    }

    @Test
    fun gb18030JapaneseIsNotMistakenForLatin() {
        val title = "六兆年と一夜物語"
        assertEquals(title, TagTextDecoder.decode(title.toByteArray(Charset.forName("GB18030"))))
    }

    @Test
    fun westernAccentsAndWindowsPunctuationStayReadable() {
        for (title in listOf("Hélène", "Björk", "Déjà vu", "Café", "Österreich", "été")) {
            assertEquals(title, TagTextDecoder.decode(title.toByteArray(Charsets.ISO_8859_1)))
        }
        val title = "Don’t Stop — Live"
        assertEquals(title, TagTextDecoder.decode(title.toByteArray(Charset.forName("windows-1252"))))
    }

    @Test
    fun declaredUnicodeRejectsMalformedBytesInsteadOfGuessing() {
        assertNull(TagTextDecoder.decodeDeclared(byteArrayOf(0xE9.toByte()), Charsets.UTF_8))
        assertNull(TagTextDecoder.decodeDeclared(byteArrayOf(0x41), Charsets.UTF_16BE))
        assertEquals("First", TagTextDecoder.decodeDeclared("First\u0000Second".toByteArray(), Charsets.UTF_8))
    }

    @Test
    fun utf8ChineseStaysUtf8() {
        val text = "青花瓷"
        assertEquals(text, TagTextDecoder.decode(text.toByteArray(StandardCharsets.UTF_8)))
    }

    @Test
    fun gbkChineseDecodes() {
        val text = "青花瓷"
        assertEquals(text, TagTextDecoder.decode(text.toByteArray(Charset.forName("GBK"))))
    }

    @Test
    fun latinAccentedDoesNotBecomeCjk() {
        val text = "Hélène"
        assertEquals(text, TagTextDecoder.decode(text.toByteArray(StandardCharsets.ISO_8859_1)))
    }

    @Test
    fun asciiIsUnchanged() {
        assertEquals("Track 01", TagTextDecoder.decode("Track 01".toByteArray(StandardCharsets.US_ASCII)))
    }

    @Test
    fun utf16LeBomDecodes() {
        val text = "夜に駆ける"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        assertEquals(text, TagTextDecoder.decode(bytes))
    }

    @Test
    fun trailingNullsAreStripped() {
        val encoded = "雨".toByteArray(Charset.forName("GBK")) + byteArrayOf(0, 0)
        assertEquals("雨", TagTextDecoder.decode(encoded))
    }
}
