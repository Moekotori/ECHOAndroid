package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyricsFormat
import org.junit.Assert.*
import org.junit.Test

class EchoSplParserTest {
    @Test fun parsesWordsDelayedStartTranslationAndEndMarker() {
        val lyrics = EchoLyricsParser.parse("""
            [offset:100]
            [00:01.00]<00:01.50>你[00:02.00]好[00:03.00]
            [00:01.00]Hello
            Bonjour
            [00:04.00]Next
            [00:05.00]
            [00:05.00]Final
        """.trimIndent(), "track.spl")
        assertEquals(EchoLyricsFormat.Spl, lyrics.format)
        assertEquals(3, lyrics.lines.size)
        val first = lyrics.lines.first()
        assertEquals("你好", first.text)
        assertEquals("Hello\nBonjour", first.translation)
        assertEquals(1_100L, first.startMs)
        assertEquals(3_100L, first.endMs)
        assertEquals(listOf(1_600L, 2_100L), first.words.map { it.startMs })
        assertEquals(5_100L, lyrics.lines[1].endMs)
        assertNull(lyrics.lines[2].endMs)
    }

    @Test fun repeatsPlainLinesAndIgnoresBackwardWordTags() {
        val lyrics = EchoLyricsParser.parse("""
            [00:01.00][00:05.00]Repeat
            [00:07.00]A[00:06.00]B[00:08.00]C[00:09.00]
        """.trimIndent(), "track.spl")
        assertEquals(listOf(1_000L, 5_000L, 7_000L), lyrics.lines.map { it.startMs })
        assertEquals("ABC", lyrics.lines.last().text)
        assertEquals(listOf(7_000L, 8_000L), lyrics.lines.last().words.map { it.startMs })
    }
}
