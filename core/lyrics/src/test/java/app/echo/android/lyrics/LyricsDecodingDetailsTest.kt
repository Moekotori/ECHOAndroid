package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyrics
import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

class LyricsDecodingDetailsTest {
    @Test fun recordsSelectedEncodingAndKeepsItThroughTheLyricsCache() {
        val text = "[00:01.00]歌词"
        val bytes = text.toByteArray(Charset.forName("GB18030"))
        val decoded = EchoLyricsTextDecoder.decodeResult(bytes)!!
        assertEquals(text, decoded.text)
        assertEquals("GB18030", decoded.charset)
        assertFalse(decoded.lossy)
        assertEquals(EchoLyricsTextDecoder.decode(bytes), decoded.text)
        val lyrics = decoded.attach(EchoLyricsParser.parse(decoded.text, "sidecar"))
        val cached = EchoLyricsJson.decode(EchoLyricsJson.encode(lyrics))
        assertEquals("GB18030", cached.metadata["text_encoding"])
        assertEquals("sidecar", cached.sourceLabel)
    }
    @Test fun bomAndReplacementFallbackAreReportedWithoutChangingCompatibilityDecoding() {
        val bom = byteArrayOf(0xff.toByte(), 0xfe.toByte()) + "[00:01]Hello".toByteArray(Charsets.UTF_16LE)
        assertEquals("UTF-16LE", EchoLyricsTextDecoder.decodeResult(bom)!!.charset)
        val damaged = "a".repeat(200).toByteArray() + byteArrayOf(0x81.toByte())
        val result = EchoLyricsTextDecoder.decodeResult(damaged)!!
        assertTrue(result.lossy)
        assertEquals("true", result.attach(EchoLyrics()).metadata["text_decoding_lossy"])
    }
}
