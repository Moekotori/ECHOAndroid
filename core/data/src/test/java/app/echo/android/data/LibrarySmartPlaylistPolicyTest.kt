package app.echo.android.data

import app.echo.android.model.library.LibrarySmartPlaylistKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySmartPlaylistPolicyTest {
    @Test
    fun kindIdsAreStableAndNotEditable() {
        LibrarySmartPlaylistKind.entries.forEach { kind ->
            val playlist = LibrarySmartPlaylistPolicy.playlist(kind, trackCount = 4, artworkUri = "art")
            assertEquals(kind.id, playlist.id)
            assertTrue(playlist.isSmartPlaylist)
            assertFalse(playlist.isLikedSongs)
            assertFalse(playlist.canEdit)
            assertFalse(playlist.canRemoveTracks)
            assertEquals(4, playlist.trackCount)
        }
    }

    @Test
    fun pinnedOrderIsRecentFrequentNeverAdded() {
        val pinned = LibrarySmartPlaylistPolicy.pinned(
            LibrarySmartPlaylistStats(
                recentCount = 1,
                frequentCount = 2,
                neverCount = 3,
                addedCount = 4,
            ),
        )
        assertEquals(
            listOf(
                LibrarySmartPlaylistKind.Recent.id,
                LibrarySmartPlaylistKind.Frequent.id,
                LibrarySmartPlaylistKind.Never.id,
                LibrarySmartPlaylistKind.Added.id,
            ),
            pinned.map { it.id },
        )
        assertEquals(listOf(1, 2, 3, 4), pinned.map { it.trackCount })
    }

    @Test
    fun unknownIdsAreNotSmartPlaylists() {
        assertFalse(LibrarySmartPlaylistPolicy.isSmartPlaylistId("local:liked"))
        assertFalse(LibrarySmartPlaylistPolicy.isSmartPlaylistId("mediastore:abc"))
        assertTrue(LibrarySmartPlaylistPolicy.isSmartPlaylistId("local:never"))
    }
}
