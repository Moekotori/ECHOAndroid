package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicSyncPolicyTest {
    @Test
    fun search3TriesEmptyQueryBeforeWildcard() {
        assertEquals(listOf("", "*"), SubsonicSyncPolicy.Search3QueryAttempts)
    }

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
    fun unknownExpectedCountDoesNotReplaceAlbumFanOut() {
        assertFalse(SubsonicSyncPolicy.shouldPreferSearch3Bulk(expectedSongCount = 0, bulkSongCount = 8))
        assertFalse(SubsonicSyncPolicy.shouldPreferSearch3Bulk(expectedSongCount = 0, bulkSongCount = 0))
    }

    @Test
    fun incompleteSearch3BulkDoesNotAuthorizeMissingRowDeletion() {
        assertFalse(
            SubsonicSyncPolicy.shouldAuthorizeMissingRowDeletion(
                usedSearch3 = true,
                expectedSongCount = 0,
                bulkSongCount = 8,
                existingRemoteCount = 120,
            ),
        )
        assertFalse(
            SubsonicSyncPolicy.shouldAuthorizeMissingRowDeletion(
                usedSearch3 = true,
                expectedSongCount = 40,
                bulkSongCount = 12,
                existingRemoteCount = 40,
            ),
        )
        assertFalse(
            SubsonicSyncPolicy.shouldAuthorizeMissingRowDeletion(
                usedSearch3 = true,
                expectedSongCount = 40,
                bulkSongCount = 40,
                existingRemoteCount = 400,
            ),
        )
        assertTrue(
            SubsonicSyncPolicy.shouldAuthorizeMissingRowDeletion(
                usedSearch3 = true,
                expectedSongCount = 40,
                bulkSongCount = 40,
                existingRemoteCount = 40,
            ),
        )
        assertTrue(
            SubsonicSyncPolicy.shouldAuthorizeMissingRowDeletion(
                usedSearch3 = false,
                expectedSongCount = 0,
                bulkSongCount = 8,
                existingRemoteCount = 120,
            ),
        )
    }

    @Test
    fun failedPlaylistFetchDoesNotReplaceLocalCopy() {
        assertFalse(
            SubsonicSyncPolicy.shouldReplaceSyncedPlaylist(
                fetchSucceeded = false,
                remoteSongCount = 0,
                matchedTrackCount = 0,
            ),
        )
    }

    @Test
    fun unmatchedRemoteSongsDoNotWipePlaylist() {
        assertFalse(
            SubsonicSyncPolicy.shouldReplaceSyncedPlaylist(
                fetchSucceeded = true,
                remoteSongCount = 8,
                matchedTrackCount = 0,
            ),
        )
    }

    @Test
    fun emptyOrPartialRemotePlaylistMayReplace() {
        assertTrue(
            SubsonicSyncPolicy.shouldReplaceSyncedPlaylist(
                fetchSucceeded = true,
                remoteSongCount = 0,
                matchedTrackCount = 0,
            ),
        )
        assertTrue(
            SubsonicSyncPolicy.shouldReplaceSyncedPlaylist(
                fetchSucceeded = true,
                remoteSongCount = 8,
                matchedTrackCount = 3,
            ),
        )
    }

    @Test
    fun unchangedRemotePlaylistDoesNotRewriteLocalRows() {
        assertFalse(
            SubsonicSyncPolicy.shouldRewriteSyncedPlaylist(
                existingName = "Favs",
                existingTrackIds = listOf("s1", "s2"),
                incomingName = "Favs",
                incomingTrackIds = listOf("s1", "s2"),
            ),
        )
        assertTrue(
            SubsonicSyncPolicy.shouldRewriteSyncedPlaylist(
                existingName = "Favs",
                existingTrackIds = listOf("s1", "s2"),
                incomingName = "Favorites",
                incomingTrackIds = listOf("s1", "s2"),
            ),
        )
        assertTrue(
            SubsonicSyncPolicy.shouldRewriteSyncedPlaylist(
                existingName = "Favs",
                existingTrackIds = listOf("s1"),
                incomingName = "Favs",
                incomingTrackIds = listOf("s1", "s2"),
            ),
        )
        assertTrue(
            SubsonicSyncPolicy.shouldRewriteSyncedPlaylist(
                existingName = null,
                existingTrackIds = null,
                incomingName = "Favs",
                incomingTrackIds = listOf("s1"),
            ),
        )
    }
}
