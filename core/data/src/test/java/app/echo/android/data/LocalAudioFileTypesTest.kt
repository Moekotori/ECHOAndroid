package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAudioFileTypesTest {
    @Test
    fun safAndWebDavShareDsfAndM4b() {
        assertEquals("audio/dsf", LocalAudioFileTypes.mimeTypeForFileName("track.dsf"))
        assertEquals("audio/dff", LocalAudioFileTypes.mimeTypeForFileName("track.dff"))
        assertEquals("audio/mp4", LocalAudioFileTypes.mimeTypeForFileName("book.m4b"))
        assertEquals("audio/aiff", LocalAudioFileTypes.mimeTypeForFileName("mix.aifc"))
        assertEquals("audio/x-matroska", LocalAudioFileTypes.mimeTypeForFileName("live.mka"))
    }

    @Test
    fun audioMimeIsAcceptedEvenWithoutKnownExtension() {
        assertTrue(LocalAudioFileTypes.isSupported("song.bin", "audio/flac"))
        assertTrue(LocalAudioFileTypes.isSupported("track", "application/ogg"))
        assertTrue(LocalAudioFileTypes.isSupported("track", "application/x-flac"))
        assertFalse(LocalAudioFileTypes.isSupported("notes.txt", "text/plain"))
        assertFalse(LocalAudioFileTypes.isSupported("track.wv", null))
    }

    @Test
    fun ffmpegBackedContainersAreImportedByExtension() {
        assertEquals("audio/ac3", LocalAudioFileTypes.mimeTypeForFileName("surround.ac3"))
        assertEquals("audio/eac3", LocalAudioFileTypes.mimeTypeForFileName("atmos.eac3"))
        assertEquals("audio/eac3", LocalAudioFileTypes.mimeTypeForFileName("atmos.ec3"))
        assertEquals("audio/vnd.dts", LocalAudioFileTypes.mimeTypeForFileName("film.dts"))
        assertEquals("audio/amr", LocalAudioFileTypes.mimeTypeForFileName("memo.amr"))
        assertTrue(LocalAudioFileTypes.isSupported("surround.ac3", null))
        assertTrue(LocalAudioFileTypes.isSupported("film.dts", null))
        assertTrue(LocalAudioFileTypes.isSupported("memo.amr", null))
    }

    @Test
    fun apeIsNotImportedBecauseTheDecoderIsNotBuilt() {
        assertFalse(LocalAudioFileTypes.isSupported("album.ape", null))
        assertFalse(LocalAudioFileTypes.isSupported("album.ape", "audio/ape"))
        assertEquals(null, LocalAudioFileTypes.mimeTypeForFileName("album.ape"))
    }

    @Test
    fun videoMimeIsNotRescuedByAudioFileExtension() {
        assertFalse(LocalAudioFileTypes.isSupported("clip.mp4", "video/mp4"))
        assertFalse(LocalAudioFileTypes.isSupported("clip.m4a", "video/mp4"))
        assertTrue(LocalAudioFileTypes.isSupported("song.mp4", "audio/mp4"))
        assertFalse(LocalAudioFileTypes.isSupported("song.mp4", null))
        assertTrue(LocalAudioFileTypes.isSupported("song.m4a", "audio/mp4"))
        assertTrue(LocalAudioFileTypes.isSupported("song.m4a", null))
    }

    @Test
    fun cueSheetsAreNotAudioFiles() {
        assertTrue(LocalAudioFileTypes.isCueSheet("album.cue", null))
        assertFalse(LocalAudioFileTypes.isSupported("album.cue", "text/plain"))
        assertTrue(LocalAudioFileTypes.isSupported("album.flac", null))
    }
}
