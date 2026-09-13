package app.echo.android.ui.home

import app.echo.android.data.LibraryAlbumListenStatsRow
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRediscoveryPolicyTest {
    private val dayMs = 24L * 60 * 60 * 1000
    private val now = 200 * dayMs

    private fun row(key: String, lastPlayed: Long, count: Int = 1) = LibraryAlbumListenStatsRow(
        albumKey = key, title = key, albumArtist = null, artist = "Artist", artworkUri = null,
        trackCount = 3, durationMs = 600_000, year = null, addedAtSeconds = 0,
        playCount = count, lastPlayedAtEpochMs = lastPlayed, favoritedAtEpochMs = 0,
    )

    @Test fun requiresHistoryAndThirtyFullDays() {
        val result = rediscoverHomeAlbums(listOf(
            row("never", 0), row("no-plays", now - 50 * dayMs, 0),
            row("future", now + dayMs), row("recent", now - 30 * dayMs + 1),
            row("boundary", now - 30 * dayMs), row("older", now - 40 * dayMs),
        ), now)
        assertEquals(listOf("older", "boundary"), result.map { it.albumKey })
    }

    @Test fun limitsUniqueResultsWithOldestFirst() {
        val rows = (0..8).map { row("album-$it", now - (40 + it) * dayMs) }
        val result = rediscoverHomeAlbums(rows + rows.last(), now)
        assertEquals(listOf("album-8", "album-7", "album-6", "album-5", "album-4", "album-3"), result.map { it.albumKey })
    }
}
