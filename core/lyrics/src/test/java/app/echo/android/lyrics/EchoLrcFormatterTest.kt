package app.echo.android.lyrics

import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyrics
import app.echo.android.model.lyrics.EchoLyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class EchoLrcFormatterTest {
    @Test
    fun embeddedWriteAllowsLosslessPlainAndSyncedLyrics() {
        for (lyrics in listOf(
            EchoLyricsParser.parse("First\nSecond", "song.txt"),
            EchoLyricsParser.parse("[00:01.00]Original\n[00:01.00]译文\n[00:02.00]Next", "song.lrc"),
        )) {
            assertEquals(EchoLrcFormatter.format(lyrics), EchoLrcFormatter.formatForEmbeddedWriteOrNull(lyrics))
        }
    }

    @Test
    fun embeddedWriteRejectsLossOfTimingAndVocalFields() {
        val line = EchoLyricLine(1000, text = "Words")
        for (richLine in listOf(
            line.copy(words = listOf(EchoLyricWord(1000, 2000, "Words"))),
            line.copy(romanization = "roma"),
            line.copy(speaker = "v2"),
            line.copy(isBackground = true),
            line.copy(endMs = 2000),
            line.copy(startMs = 1001),
        )) {
            assertNull(EchoLrcFormatter.formatForEmbeddedWriteOrNull(EchoLyrics(listOf(richLine))))
        }
    }

    @Test
    fun embeddedWriteRejectsEmptyTimedLyrics() {
        val lyrics = EchoLyricsParser.parse("[00:01.00]\n[00:02.00]", "empty.lrc")
        assertNull(EchoLrcFormatter.formatForEmbeddedWriteOrNull(lyrics))
    }

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

    @Test
    fun multilineTextAndTranslationKeepAllWordsOnFileRoundTrip() {
        for (separator in listOf("\n", "\r\n", "\r")) {
            val lyrics = EchoLyrics(lines = listOf(EchoLyricLine(1000,
                text = "First${separator}Second", translation = "译文一${separator}译文二")))
            for (formatted in listOf(EchoLrcFormatter.format(lyrics), EchoLyricsEditing.toLrc(lyrics))) {
                val parsed = EchoLrcParser.parse(formatted).lines.single()
                assertEquals("First Second", parsed.text)
                assertEquals("译文一 译文二", parsed.translation)
            }
        }
    }
}
