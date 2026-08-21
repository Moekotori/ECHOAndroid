package app.echo.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoIncomingAudioTest {
    @Test
    fun mediaStoreContentUrisMapToLibraryIds() {
        assertEquals(
            "mediastore:42",
            EchoIncomingAudio.libraryTrackIdForMediaStoreUri("content://media/external/audio/media/42"),
        )
        assertEquals(
            "mediastore:9",
            EchoIncomingAudio.libraryTrackIdForMediaStoreUri("content://media/external/audio/media/9?foo=1"),
        )
        assertEquals(
            "mediastore:7",
            EchoIncomingAudio.libraryTrackIdForMediaStoreUri(
                "content://com.android.providers.media.documents/document/audio%3A7",
            ),
        )
    }

    @Test
    fun unrelatedUrisStayUnmapped() {
        assertNull(EchoIncomingAudio.libraryTrackIdForMediaStoreUri("content://com.example.files/doc/1"))
        assertNull(EchoIncomingAudio.libraryTrackIdForMediaStoreUri("file:///sdcard/Music/song.flac"))
        assertNull(EchoIncomingAudio.libraryTrackIdForMediaStoreUri(""))
    }

    @Test
    fun viewAndShareActionsAreIncoming() {
        assertTrue(EchoIncomingAudio.isIncomingAudioAction("android.intent.action.VIEW"))
        assertTrue(EchoIncomingAudio.isIncomingAudioAction("android.intent.action.SEND"))
        assertTrue(EchoIncomingAudio.isIncomingAudioAction("android.intent.action.SEND_MULTIPLE"))
        assertFalse(EchoIncomingAudio.isIncomingAudioAction("android.intent.action.MAIN"))
        assertFalse(EchoIncomingAudio.isIncomingAudioAction(null))
    }
}
