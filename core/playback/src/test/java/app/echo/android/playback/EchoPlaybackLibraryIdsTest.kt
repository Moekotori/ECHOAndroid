package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoPlaybackLibraryIdsTest {
    @Test
    fun albumArtistPlaylistIdsRoundTrip() {
        assertEquals("kind of blue", EchoPlaybackLibraryIds.albumKey(EchoPlaybackLibraryIds.album("kind of blue")))
        assertEquals("miles", EchoPlaybackLibraryIds.artistKey(EchoPlaybackLibraryIds.artist("miles")))
        assertEquals("pl-1", EchoPlaybackLibraryIds.playlistId(EchoPlaybackLibraryIds.playlist("pl-1")))
    }

    @Test
    fun prefixesDoNotCollideWithTrackIds() {
        assertTrue(EchoPlaybackLibraryIds.isBrowsableCollection(EchoPlaybackLibraryIds.ROOT))
        assertTrue(EchoPlaybackLibraryIds.isBrowsableCollection(EchoPlaybackLibraryIds.album("a")))
        assertTrue(EchoPlaybackLibraryIds.isTrackMediaId("mediastore:12"))
        assertFalse(EchoPlaybackLibraryIds.isTrackMediaId(EchoPlaybackLibraryIds.ALBUMS))
        assertFalse(EchoPlaybackLibraryIds.isTrackMediaId(""))
        assertNull(EchoPlaybackLibraryIds.albumKey(EchoPlaybackLibraryIds.ARTISTS))
    }

    @Test
    fun browseRangeCoercesPageAndSize() {
        assertEquals(100 to 0, EchoPlaybackLibraryIds.browseRange(0, 10_000))
        assertEquals(1 to 0, EchoPlaybackLibraryIds.browseRange(-3, 0))
        assertEquals(25 to 50, EchoPlaybackLibraryIds.browseRange(2, 25))
    }
}
