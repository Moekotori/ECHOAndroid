package app.echo.android.feature.library

import app.echo.android.model.library.EchoTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumTrackFactsTest {
    @Test
    fun hiResNeedsALosslessFileAtOrAbove88kHz() {
        assertTrue(albumSpec(listOf(track("a", "audio/flac", 96_000))).hiRes)
        assertFalse(albumSpec(listOf(track("a", "audio/flac", 44_100))).hiRes)
        assertFalse(albumSpec(listOf(track("a", "audio/mpeg", 96_000))).hiRes)
        assertFalse(albumSpec(listOf(track("a", null, null))).hiRes)
    }

    @Test
    fun outlierIsOnlyFlaggedAgainstADominantSpec() {
        val tracks = listOf(
            track("1", "audio/flac", 96_000),
            track("2", "audio/flac", 96_000),
            track("3", "audio/flac", 96_000),
            track("4", "audio/mpeg", 44_100),
        )
        val dominant = albumSpec(tracks).dominant
        assertEquals(TrackSpec("FLAC", 96_000), dominant)
        assertNull(trackSpecOutlier(tracks[0], dominant))
        assertEquals("MP3 44.1", trackSpecOutlier(tracks[3], dominant))

        val even = albumSpec(listOf(track("1", "audio/flac", 96_000), track("2", "audio/mpeg", 44_100)))
        assertNull(even.dominant)
        assertNull(trackSpecOutlier(tracks[3], even.dominant))
    }

    @Test
    fun missingSpecFieldsAreNotOutliers() {
        val dominant = TrackSpec("FLAC", 96_000)
        assertNull(trackSpecOutlier(track("x", "audio/flac", null), dominant))
        assertNull(trackSpecOutlier(track("y", null, null), dominant))
    }

    @Test
    fun sizeIsNullUnlessEveryTrackReportsOne() {
        assertEquals(30L, albumSpec(listOf(track("a", size = 10), track("b", size = 20))).sizeBytes)
        assertNull(albumSpec(listOf(track("a", size = 10), track("b", size = 0))).sizeBytes)
    }

    @Test
    fun sampleRateRangeIsCompact() {
        assertEquals("44.1–96 kHz", formatSampleRates(listOf(44_100, 96_000)))
        assertEquals("48 kHz", formatSampleRates(listOf(48_000)))
        assertNull(formatSampleRates(emptyList()))
    }

    @Test
    fun discTotalsAndGenreCountEveryLoadedTrack() {
        val facts = albumTrackFacts(
            listOf(
                track("1", disc = 1, durationMs = 60_000, genre = "Rock"),
                track("2", disc = 1, durationMs = 30_000, genre = "Rock"),
                track("3", disc = 2, durationMs = 10_000, genre = "Pop"),
            ),
        )
        assertTrue(facts.multiDisc)
        assertEquals(AlbumDiscTotals(2, 90_000), facts.discs[1])
        assertEquals(AlbumDiscTotals(1, 10_000), facts.discs[2])
        assertEquals("Rock", facts.genre)
    }

    private fun track(
        id: String,
        mime: String? = null,
        rate: Int? = null,
        size: Long = 0L,
        disc: Int? = null,
        durationMs: Long = 0L,
        genre: String? = null,
    ) = EchoTrack(
        id = id,
        uri = "content://$id",
        title = id,
        artist = "Artist",
        mimeType = mime,
        sampleRateHz = rate,
        sizeBytes = size,
        discNumber = disc,
        durationMs = durationMs,
        genre = genre,
    )
}
