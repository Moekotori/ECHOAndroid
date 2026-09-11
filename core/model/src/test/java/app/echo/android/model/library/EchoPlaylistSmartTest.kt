package app.echo.android.model.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoPlaylistSmartTest {
    @Test
    fun smartKindRoundTripsFixedIds() {
        assertEquals(LibrarySmartPlaylistKind.Recent, LibrarySmartPlaylistKind.fromId("local:recent"))
        assertEquals(LibrarySmartPlaylistKind.Frequent, LibrarySmartPlaylistKind.fromId(" local:frequent "))
        assertEquals(null, LibrarySmartPlaylistKind.fromId("local:liked"))
        assertEquals(null, LibrarySmartPlaylistKind.fromId(""))
    }

    @Test
    fun likedSongsStayRemovableAndNotSmart() {
        val liked = EchoPlaylist(id = EchoPlaylist.LikedSongsId, name = "Liked")
        assertTrue(liked.isLikedSongs)
        assertFalse(liked.isSmartPlaylist)
        assertFalse(liked.canEdit)
        assertTrue(liked.canRemoveTracks)
    }
}
