package app.echo.android.data

import app.echo.android.model.library.EchoTrack
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class LocalScanMetadataTest {
    @Test
    fun swallowedIoFailureStillRequestsARetryAndClosesInput() {
        var closed = false
        val input = object : InputStream() {
            override fun read(): Int = throw IOException("temporarily unavailable")
            override fun close() { closed = true }
        }
        assertThrows(IOException::class.java) {
            readScanMetadata(input) { stream -> try { stream.read() } catch (_: IOException) { -1 } }
        }
        assertTrue(closed)
        assertNull(readScanMetadata(byteArrayOf().inputStream()) { null })
    }

    @Test
    fun failedReadKeepsOldTagsAndRemainsPendingUntilSuccessfulRead() {
        val old = EchoTrack("mediastore:1", "content://audio/1", "Real title", "Artist",
            album = "Album", durationMs = 120_000L).toLibraryTrackEntity()
        val incoming = old.copy(title = "file.mp3", artist = "Unknown", album = null,
            durationMs = 0, dateModifiedSeconds = 9, relativePath = "Music/Moved/",
            fingerprint = LibraryScanPolicy.PendingDocumentMetadataFingerprint)
        val kept = retainFailedScanMetadata(incoming, old).withScanMetadata(1)
        assertEquals(listOf(old.title, old.artist, old.album), listOf(kept.title, kept.artist, kept.album))
        assertEquals(old.durationMs, kept.durationMs)
        assertEquals(incoming.relativePath, kept.relativePath)
        assertEquals(incoming.dateModifiedSeconds, kept.dateModifiedSeconds)
        assertEquals(LibraryScanPolicy.PendingDocumentMetadataFingerprint, kept.fingerprint)
        assertSame(old, retainFailedScanMetadata(old, incoming))
    }
}
