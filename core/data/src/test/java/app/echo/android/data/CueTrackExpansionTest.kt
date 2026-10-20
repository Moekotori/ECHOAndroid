package app.echo.android.data

import app.echo.android.model.library.CueSheet
import app.echo.android.model.library.CueSheetTrack
import app.echo.android.model.library.EchoTrack
import org.junit.Assert.*
import org.junit.Test

class CueTrackExpansionTest {
    private fun source(duration: Long = 120_000L) =
        EchoTrack("saf:file", "content://audio/file", "Source", "Artist", durationMs = duration).toLibraryTrackEntity()

    @Test
    fun preservesSourceUriAndCreatesBoundedPlaybackClips() {
        val tracks = source().splitByCue(CueSheet(tracks = listOf(
            CueSheetTrack(1, "First", startMs = 0L), CueSheetTrack(2, "Second", startMs = 60_000L),
        )))
        assertEquals(listOf("saf:file#cue:1", "saf:file#cue:2"), tracks.map { it.id })
        assertEquals(listOf(0L, 60_000L), tracks.map { it.clipStartMs })
        assertEquals(listOf(60_000L, 120_000L), tracks.map { it.clipEndMs })
        assertEquals(listOf(60_000L, 60_000L), tracks.map { it.durationMs })
        assertEquals("content://audio/file#echo-cue=2", tracks[1].contentUri)
    }

    @Test
    fun invalidRangesKeepWholeFileAndUnknownDurationKeepsExplicitLeadOut() {
        val source = source()
        assertEquals(listOf(source), source.splitByCue(CueSheet(tracks = listOf(
            CueSheetTrack(1, "First", startMs = 90_000L), CueSheetTrack(2, "Bad", startMs = 30_000L),
        ))))
        val bounded = source(0).splitByCue(CueSheet(tracks = listOf(
            CueSheetTrack(1, "First", startMs = 0L, endMs = 60_000L),
        ))).single()
        assertEquals(60_000L, bounded.clipEndMs)
        assertEquals(60_000L, bounded.durationMs)
    }
}
