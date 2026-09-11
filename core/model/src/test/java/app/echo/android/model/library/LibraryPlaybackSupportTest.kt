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
    fun dsdMimeAndExtensionsArePlayable() {
        assertTrue(LibraryPlaybackSupport.isPlayableOnPhone("audio/dsf", null))
        assertTrue(LibraryPlaybackSupport.isPlayableOnPhone("audio/dff", "track.dff"))
        assertTrue(LibraryPlaybackSupport.isPlayableOnPhone("audio/x-dsd", null))
        assertTrue(LibraryPlaybackSupport.isPlayableOnPhone(null, "Music/album/song.dsf"))
    }

    @Test
    fun dsdDetectionUsesMimeAndExtension() {
        assertTrue(LibraryPlaybackSupport.isDsd("audio/dsf", null))
        assertTrue(LibraryPlaybackSupport.isDsd("audio/dff", "track.dff"))
        assertTrue(LibraryPlaybackSupport.isDsd(null, "Music/album/song.dsf"))
        assertFalse(LibraryPlaybackSupport.isDsd("audio/flac", "song.flac"))
        assertFalse(LibraryPlaybackSupport.isDsd(null, null))
    }
}
