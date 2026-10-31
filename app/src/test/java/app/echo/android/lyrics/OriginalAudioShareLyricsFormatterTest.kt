package app.echo.android.lyrics

import app.echo.android.model.lyrics.*
import org.junit.Assert.*
import org.junit.Test

class OriginalAudioShareLyricsFormatterTest {
    @Test fun enhancedLrcUsesWholeOriginalTimeWhileJsonPreservesWordEndsAndVocals() {
        val original = EchoLyrics(lines = listOf(EchoLyricLine(125, 800, "hello", translation = "你好", speaker = "v1",
            words = listOf(EchoLyricWord(125, 800, "hello")))))
        val result = OriginalAudioShareLyricsFormatter.format(original, -25, 60_000)!!
        assertEquals("lrc", result.extension)
        assertTrue(result.text.contains("[01:00.100]<01:00.100>hello"))
        assertTrue(result.text.contains("[01:00.100]你好"))
        val complete = EchoLyricsJson.decode(result.completeJson!!)
        assertEquals(60_775L, complete.lines.single().words.single().endMs)
        assertEquals("v1", complete.lines.single().speaker)
        assertEquals(125L, original.lines.single().startMs)
    }

    @Test fun ordinaryLrcKeepsMillisecondPrecisionAndPlainTextStaysReadable() {
        val synced = OriginalAudioShareLyricsFormatter.format(EchoLyrics(lines = listOf(EchoLyricLine(123, text = "line"))), 0, 0)!!
        assertTrue(synced.text.contains("[00:00.123]line"))
        assertNull(synced.completeJson)
        val plain = OriginalAudioShareLyricsFormatter.format(EchoLyrics(lines = listOf(EchoLyricLine(-1, text = "words"))), 0, 0)!!
        assertEquals("txt", plain.extension)
        assertEquals("words\n", plain.text)
    }
}
