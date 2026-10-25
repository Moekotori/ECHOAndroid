package app.echo.android.data

import app.echo.android.model.library.EchoTrack
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class LocalScanMetadataTest {
    @Test
    fun scanDecoratorPreservesLegacyTailTagsAndRandomAccess() {
        val trailer = ByteArray(128).also {
            "TAG".toByteArray().copyInto(it)
            "Tail title".toByteArray().copyInto(it, 3)
            "Tail artist".toByteArray().copyInto(it, 33)
        }
        val bytes = ByteArray(128) + trailer
        val file = java.io.File.createTempFile("echo-scan-tail-", ".mp3")
        try {
            file.writeBytes(bytes)
            assertEquals("Tail title", readScanMetadata(file.inputStream(), ::readLocalAudioTags)?.title)
            val ranges = RandomAccessTagInput(bytes.size.toLong()) { at, count ->
                bytes.copyOfRange(at.toInt(), at.toInt() + count)
            }
            assertEquals("Tail artist", readScanMetadata(ranges, ::readLocalAudioTags)?.artist)
        } finally { file.delete() }
    }

    @Test
    fun tailFailuresCannotBeSwallowedAndOptionalFailuresRemainPending() {
        val input = object : InputStream(), TagTailReader {
            override fun read(): Int = -1
            override fun tail(count: Int): ByteArray? = throw IOException("tail unavailable")
        }
        assertThrows(IOException::class.java) {
            readScanMetadata(input) { stream ->
                try { (stream as TagTailReader).tail(32) } catch (_: IOException) { null }
            }
        }
        val reads = ScanMetadataReads()
        assertNull(reads.optional({ null }) { null })
        assertTrue(reads.failed)
        assertEquals(1, reads.optional({ byteArrayOf(1).inputStream() }) { it.read() })
        assertTrue(reads.failed) // A later successful fallback must not erase the failure.
        val absent = ScanMetadataReads()
        assertNull(absent.optional({ byteArrayOf().inputStream() }) { null })
        assertFalse(absent.failed) // Successfully read, genuinely absent tags are cacheable.
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            absent.optional({ throw kotlinx.coroutines.CancellationException() }) { null }
        }
    }

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
