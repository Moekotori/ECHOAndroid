package app.echo.android.lyrics

import app.echo.android.model.lyrics.*
import org.junit.Assert.*
import org.junit.Test

class EchoLyricsEditingTest {
    @Test fun timestampsUseMillisecondsAndValidateSeconds() {
        assertEquals(62345L,EchoLyricsEditing.parseTimestamp("01:02.345"))
        assertEquals(62050L,EchoLyricsEditing.parseTimestamp("01:02.05"))
        assertNull(EchoLyricsEditing.parseTimestamp("01:61.000"))
        assertEquals("01:02.345",EchoLyricsEditing.timestamp(62345))
    }
    @Test fun editedTranslationRoundTripsThroughLrc() {
        val edited = EchoLyricsEditing.finish(EchoLyrics(),listOf(EchoLyricLine(1000,text = "Original",translation = "Translation"),EchoLyricLine(2000,text = "Next")))
        val parsed = EchoLrcParser.parse(EchoLyricsEditing.toLrc(edited))
        assertEquals("Translation",parsed.lines.first().translation)
        assertEquals(2000L,parsed.lines.first().endMs)
    }
    @Test(expected = IllegalArgumentException::class) fun reversedTimingDoesNotSilentlyReorderLines() {
        EchoLyricsEditing.finish(EchoLyrics(),listOf(EchoLyricLine(2000,text="A"),EchoLyricLine(1000,text="B")))
    }

    @Test fun unchangedSavePreservesSilenceGapsAndFinalEnd() {
        val original = EchoLyrics(lines = listOf(
            EchoLyricLine(1000, endMs = 1500, text = "First"),
            EchoLyricLine(5000, endMs = 5500, text = "Last"),
        ))
        val saved = EchoLyricsEditing.finish(original, original.lines)
        assertEquals(listOf(1500L, 5500L), saved.lines.map { it.endMs })
        assertNull(LyricsLineAtPosition.primaryText(saved, 2000))
        assertNull(LyricsLineAtPosition.primaryText(saved, 6000))
    }
}
