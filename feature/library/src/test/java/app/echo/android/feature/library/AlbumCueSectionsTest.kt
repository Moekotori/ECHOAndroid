package app.echo.android.feature.library

import app.echo.android.model.library.ArtistSetlistSong
import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibrarySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumCueSectionsTest {
    @Test
    fun cueBannerNeedsTwoMovementsFromTheSameBase() {
        val first = track(CueSheetPolicy.cueTrackId("base", 1))
        val second = track(CueSheetPolicy.cueTrackId("base", 2))
        assertTrue(albumHasCueMovements(listOf(first, second)))
        assertFalse(albumHasCueMovements(listOf(first)))
        assertFalse(albumHasCueMovements(listOf(track("plain"), track("other"))))
        assertFalse(albumHasCueMovements(listOf(first, track(CueSheetPolicy.cueTrackId("other", 1)))))
    }

    @Test
    fun clipRangeFormatsOnlyValidCueWindows() {
        val cue = track(CueSheetPolicy.cueTrackId("base", 1), start = 60_000L, end = 125_000L)
        assertEquals("1:00–2:05", cueClipRangeLabel(cue))
        assertNull(cueClipRangeLabel(track("plain", start = 0L, end = 10_000L)))
        assertNull(formatCueClipRange(10_000L, 10_000L))
    }

    @Test
    fun playableSetlistQueueKeepsHitsInOrder() {
        assertEquals(
            listOf("a", "c"),
            playableSetlistTrackIds(
                listOf(
                    ArtistSetlistSong("One", trackId = "a"),
                    ArtistSetlistSong("Missing"),
                    ArtistSetlistSong("Two", trackId = "c"),
                    ArtistSetlistSong("Dup", trackId = "a"),
                ),
            ),
        )
    }

    private fun track(
        id: String,
        start: Long = 0L,
        end: Long = 0L,
    ): EchoTrack = EchoTrack(
        id = id,
        uri = "content://track",
        title = "Track",
        artist = "Artist",
        source = LibrarySource.MediaStore,
        clipStartMs = start,
        clipEndMs = end,
    )
}
