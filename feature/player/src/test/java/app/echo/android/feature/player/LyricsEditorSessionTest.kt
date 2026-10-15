package app.echo.android.feature.player

import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyrics
import org.junit.Assert.*
import org.junit.Test

class LyricsEditorSessionTest {
    private val lyrics = EchoLyrics(lines = listOf(EchoLyricLine(1000, endMs = 1500, text = "Loaded")))

    @Test
    fun waitsForFirstResolvedSourceThenProtectsDraftAcrossReloads() {
        val session = LyricsEditorSession(EchoLyrics(), loading = true)
        assertFalse(session.sourceReady)
        assertTrue(session.rows.isEmpty())
        session.initialize(lyrics, loading = false)
        assertTrue(session.sourceReady)
        assertEquals("Loaded", session.rows.single().text)
        session.rows[0] = session.rows[0].copy(text = "Edited")
        session.initialize(EchoLyrics(), loading = true)
        session.initialize(lyrics, loading = false)
        assertEquals("Edited", session.rows.single().text)
    }

    @Test
    fun savedStatusTracksTheExactSavedDraft() {
        val session = LyricsEditorSession(lyrics, loading = false)
        assertFalse(session.saved)
        val snapshot = session.rows.toList()
        session.markSaved(snapshot)
        assertTrue(session.saved)
        session.rows[0] = session.rows[0].copy(translation = "New translation")
        assertFalse(session.saved)
        session.markSaved(snapshot)
        assertFalse(session.saved)
        session.markSaved(session.rows.toList())
        assertTrue(session.saved)
    }

    @Test
    fun changingLineStartMovesItsExplicitEndByTheSameAmount() {
        val session = LyricsEditorSession(lyrics, loading = false)
        session.rows[0] = session.rows[0].copy(time = "00:02.000")
        val saved = session.snapshot(session.rows.toList()).lines.single()
        assertEquals(2000L, saved.startMs)
        assertEquals(2500L, saved.endMs)
    }
}
