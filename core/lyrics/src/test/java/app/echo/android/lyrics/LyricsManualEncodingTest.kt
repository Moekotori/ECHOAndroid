package app.echo.android.lyrics

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

class LyricsManualEncodingTest {
    @Test fun explicitEncodingRepairsUnmarkedTextButBomRemainsAuthoritative() {
        val text = "[00:01.00]海邊的歌"
        assertEquals(text, EchoLyricsTextDecoder.decodeResult(text.toByteArray(Charset.forName("Big5")), "Big5")!!.text)
        val bom = byteArrayOf(-17, -69, -65) + text.toByteArray(Charsets.UTF_8)
        val decoded = EchoLyricsTextDecoder.decodeResult(bom, "Big5")!!
        assertEquals(text, decoded.text)
        assertEquals("UTF-8", decoded.charset)
    }
}
