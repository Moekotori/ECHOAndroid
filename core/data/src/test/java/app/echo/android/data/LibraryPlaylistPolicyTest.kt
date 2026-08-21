package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPlaylistPolicyTest {
    @Test
    fun createAddRemoveReorderRenameAndDeleteChangeMembershipAndOrder() {
        var catalog = LibraryPlaylistCatalog()
        catalog = LibraryPlaylistPolicy.create(
            catalog = catalog,
            name = "  Late Night  ",
            id = "p1",
            nowEpochMs = 1L,
        )
        val created = catalog.playlists.single()
        assertEquals("p1", created.id)
        assertEquals("Late Night", created.name)
        assertTrue(created.trackIds.isEmpty())

        catalog = LibraryPlaylistPolicy.addTrack(catalog, "p1", "t-a", 2L)
        catalog = LibraryPlaylistPolicy.addTrack(catalog, "p1", "t-b", 3L)
        catalog = LibraryPlaylistPolicy.addTrack(catalog, "p1", "t-c", 4L)
        catalog = LibraryPlaylistPolicy.addTrack(catalog, "p1", "t-b", 5L)
        assertEquals(listOf("t-a", "t-b", "t-c"), catalog.playlists.single().trackIds)

        catalog = LibraryPlaylistPolicy.removeTrack(catalog, "p1", "t-b", 6L)
        assertEquals(listOf("t-a", "t-c"), catalog.playlists.single().trackIds)
        assertFalse(catalog.playlists.single().trackIds.contains("t-b"))

        catalog = LibraryPlaylistPolicy.reorderTracks(
            catalog = catalog,
            playlistId = "p1",
            fromIndex = 1,
            toIndex = 0,
            nowEpochMs = 7L,
        )
        assertEquals(listOf("t-c", "t-a"), catalog.playlists.single().trackIds)

        catalog = LibraryPlaylistPolicy.rename(catalog, "p1", "Dawn Mix", 8L)
        assertEquals("Dawn Mix", catalog.playlists.single().name)

        catalog = LibraryPlaylistPolicy.delete(catalog, "p1")
        assertTrue(catalog.playlists.isEmpty())
    }

    @Test
    fun invalidMutationsLeaveCatalogUnchanged() {
        val original = LibraryPlaylistPolicy.create(
            catalog = LibraryPlaylistCatalog(),
            name = "Keep",
            id = "keep",
            nowEpochMs = 1L,
        )
        assertEquals(original, LibraryPlaylistPolicy.create(original, " ", "other", 2L))
        assertEquals(original, LibraryPlaylistPolicy.rename(original, "keep", "   ", 3L))
        assertEquals(original, LibraryPlaylistPolicy.addTrack(original, "missing", "t1", 4L))
        assertEquals(original, LibraryPlaylistPolicy.removeTrack(original, "keep", "missing", 5L))
        assertEquals(original, LibraryPlaylistPolicy.reorderTracks(original, "keep", 0, 1, 6L))
        assertEquals(original, LibraryPlaylistPolicy.delete(original, "missing"))
    }

    @Test
    fun membershipsFollowRequestedTrackOrder() {
        val playlist = LibraryPlaylistRecord(
            id = "p-order",
            name = "Order",
            trackIds = listOf("third", "first", "second"),
        )
        assertEquals(
            listOf("third" to 0, "first" to 1, "second" to 2),
            LibraryPlaylistPolicy.trackMemberships(playlist),
        )
        assertEquals(listOf("third", "first", "second"), playlist.toEchoPlaylist().trackIds)
    }
}
