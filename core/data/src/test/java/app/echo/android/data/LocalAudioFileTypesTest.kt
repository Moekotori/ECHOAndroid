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
        assertFalse(LocalAudioFileTypes.isSupported("notes.txt", "text/plain"))
        assertFalse(LocalAudioFileTypes.isSupported("track.wv", null))
    }
}
