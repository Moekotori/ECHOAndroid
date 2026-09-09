package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyrics
import org.junit.Assert.assertEquals
import org.junit.Test

class EchoLrcFormatterTest {
    @Test
    fun formatsSyncedLines() {
        val lyrics = EchoLyrics(
            lines = listOf(
                EchoLyricLine(startMs = 1_000L, text = "夜曲"),
                EchoLyricLine(startMs = 2_500L, text = "为你弹奏肖邦的夜曲"),
            ),
        )
        assertEquals(
            "[00:01.00]夜曲\n[00:02.50]为你弹奏肖邦的夜曲",
            EchoLrcFormatter.format(lyrics),
        )
    }

    @Test
    fun formatsPlainLinesWithoutTimestamps() {
        val lyrics = EchoLyrics(
            lines = listOf(
                EchoLyricLine(startMs = -1L, text = "第一行"),
                EchoLyricLine(startMs = -1L, text = "第二行"),
            ),
        )
        assertEquals("第一行\n第二行", EchoLrcFormatter.format(lyrics))
    }
}
