package app.echo.android.model.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryPlaybackSupportTest {
    @Test
    fun flacAndMp3StayPlayable() {
        assertTrue(LibraryPlaybackSupport.isPlayableOnPhone("audio/flac", "song.flac"))
        assertTrue(LibraryPlaybackSupport.isPlayableOnPhone("audio/mpeg", "song.mp3"))
        assertTrue(LibraryPlaybackSupport.isPlayableOnPhone(null, null))
    }

    @Test
    fun dsdMimeAndExtensionsAreNotPlayable() {
        assertFalse(LibraryPlaybackSupport.isPlayableOnPhone("audio/dsf", null))
        assertFalse(LibraryPlaybackSupport.isPlayableOnPhone("audio/dff", "track.dff"))
        assertFalse(LibraryPlaybackSupport.isPlayableOnPhone("audio/x-dsd", null))
        assertFalse(LibraryPlaybackSupport.isPlayableOnPhone(null, "Music/album/song.dsf"))
    }
}
