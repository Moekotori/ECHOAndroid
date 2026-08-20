package app.echo.android.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicSyncPolicyTest {
    @Test
    fun emptyBulkDoesNotReplaceAlbumFanOut() {
        assertFalse(SubsonicSyncPolicy.shouldPreferSearch3Bulk(expectedSongCount = 40, bulkSongCount = 0))
    }

    @Test
    fun completeBulkSkipsAlbumFanOut() {
        assertTrue(SubsonicSyncPolicy.shouldPreferSearch3Bulk(expectedSongCount = 40, bulkSongCount = 40))
        assertTrue(SubsonicSyncPolicy.shouldPreferSearch3Bulk(expectedSongCount = 40, bulkSongCount = 41))
    }

    @Test
    fun partialBulkFallsBackToAlbums() {
        assertFalse(SubsonicSyncPolicy.shouldPreferSearch3Bulk(expectedSongCount = 40, bulkSongCount = 12))
    }

    @Test
    fun unknownExpectedCountUsesAnyBulkSongs() {
        assertTrue(SubsonicSyncPolicy.shouldPreferSearch3Bulk(expectedSongCount = 0, bulkSongCount = 8))
        assertFalse(SubsonicSyncPolicy.shouldPreferSearch3Bulk(expectedSongCount = 0, bulkSongCount = 0))
    }
}
