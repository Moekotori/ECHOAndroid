package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyrics
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

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

    @Test
    fun preservesTranslationWhenWrittenLyricsAreParsedAgain() {
        val lyrics = EchoLyrics(lines = listOf(EchoLyricLine(1000, text = "Original", translation = "译文")))
        val parsed = EchoLrcParser.parse(EchoLrcFormatter.format(lyrics))
        assertEquals("Original", parsed.lines.single().text)
        assertEquals("译文", parsed.lines.single().translation)
    }

    @Test
    fun timestampsRemainReadableWithArabicSystemLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            val formatted = EchoLrcFormatter.format(EchoLyrics(lines = listOf(EchoLyricLine(1000, text = "Words"))))
            assertEquals("[00:01.00]Words", formatted)
            assertEquals(1000L, EchoLrcParser.parse(formatted).lines.single().startMs)
        } finally {
            Locale.setDefault(previous)
        }
    }
}
