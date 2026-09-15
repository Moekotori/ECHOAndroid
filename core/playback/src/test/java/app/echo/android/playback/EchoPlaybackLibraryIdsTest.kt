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
        assertEquals("jazz", EchoPlaybackLibraryIds.genreKey(EchoPlaybackLibraryIds.genre("jazz")))
        assertEquals("Music/A", EchoPlaybackLibraryIds.folderKey(EchoPlaybackLibraryIds.folder("Music/A")))
        assertEquals("", EchoPlaybackLibraryIds.folderKey(EchoPlaybackLibraryIds.folder("")))
    }

    @Test
    fun prefixesDoNotCollideWithTrackIds() {
        assertTrue(EchoPlaybackLibraryIds.isBrowsableCollection(EchoPlaybackLibraryIds.ROOT))
        assertTrue(EchoPlaybackLibraryIds.isBrowsableCollection(EchoPlaybackLibraryIds.album("a")))
        assertTrue(EchoPlaybackLibraryIds.isBrowsableCollection(EchoPlaybackLibraryIds.FOLDERS))
        assertTrue(EchoPlaybackLibraryIds.isBrowsableCollection(EchoPlaybackLibraryIds.GENRES))
        assertTrue(EchoPlaybackLibraryIds.isBrowsableCollection(EchoPlaybackLibraryIds.RADIO))
        assertTrue(EchoPlaybackLibraryIds.isBrowsableCollection(EchoPlaybackLibraryIds.folder("")))
        assertTrue(EchoPlaybackLibraryIds.isBrowsableCollection(EchoPlaybackLibraryIds.genre("jazz")))
        assertTrue(EchoPlaybackLibraryIds.isTrackMediaId("mediastore:12"))
        assertTrue(EchoPlaybackLibraryIds.isTrackMediaId("radio:station-1"))
        assertFalse(EchoPlaybackLibraryIds.isTrackMediaId(EchoPlaybackLibraryIds.ALBUMS))
        assertFalse(EchoPlaybackLibraryIds.isTrackMediaId(EchoPlaybackLibraryIds.RADIO))
        assertFalse(EchoPlaybackLibraryIds.isTrackMediaId(""))
        assertNull(EchoPlaybackLibraryIds.albumKey(EchoPlaybackLibraryIds.ARTISTS))
        assertNull(EchoPlaybackLibraryIds.folderKey(EchoPlaybackLibraryIds.FOLDERS))
        assertNull(EchoPlaybackLibraryIds.genreKey(EchoPlaybackLibraryIds.GENRES))
    }

    @Test
    fun browseRangeCoercesPageAndSize() {
        assertEquals(100 to 0, EchoPlaybackLibraryIds.browseRange(0, 10_000))
        assertEquals(1 to 0, EchoPlaybackLibraryIds.browseRange(-3, 0))
        assertEquals(25 to 50, EchoPlaybackLibraryIds.browseRange(2, 25))
    }
}
