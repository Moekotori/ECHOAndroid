package app.echo.android.data

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Test

class TagTextDecoderTest {
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
