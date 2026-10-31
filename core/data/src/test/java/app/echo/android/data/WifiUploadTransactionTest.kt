package app.echo.android.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class WifiUploadTransactionTest {
    private class Journal : WifiUploadJournal {
        val pending = linkedMapOf<String, PendingWifiUpload>()
        override fun entries() = pending.values.toList()
        override fun put(upload: PendingWifiUpload) { pending[upload.id] = upload }
        override fun remove(id: String) { pending.remove(id) }
    }
    private class Files : WifiUploadFiles {
        val data = linkedMapOf("existing.flac" to byteArrayOf(9, 8, 7))
        var refuseDelete = false
        override fun create(upload: PendingWifiUpload): String {
            data[upload.temporaryName] = byteArrayOf()
            return upload.temporaryName
        }
        override fun open(document: String) = object : ByteArrayOutputStream() {
            override fun close() { data[document] = toByteArray() }
        }
        override fun publish(upload: PendingWifiUpload) {
            check(!data.containsKey(upload.finalName))
            data[upload.finalName] = checkNotNull(data.remove(upload.document))
        }
        override fun deleteTemporary(upload: PendingWifiUpload): Boolean {
            assertFalse("cleanup must clear the writer interrupt flag while deleting", Thread.currentThread().isInterrupted)
            if (refuseDelete) return false
            data.remove(upload.temporaryName)
            return true
        }
    }
    private fun upload(id: String = "one") = PendingWifiUpload(id, "tree", "parent", ".echo-upload-$id.part", "song.flac", "audio/flac")

    @Test fun truncatedBodyIsRemovedAndSameNameCanBeRetriedWithoutTouchingOriginals() {
        val journal = Journal(); val files = Files(); val transfer = WifiUploadTransaction(journal, files)
        assertTrue(runCatching { transfer.receive(upload(), 5, ByteArrayInputStream(byteArrayOf(1, 2))) }.isFailure)
        assertEquals(setOf("existing.flac"), files.data.keys)
        assertTrue(journal.pending.isEmpty())
        transfer.receive(upload("retry"), 3, ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        assertArrayEquals(byteArrayOf(1, 2, 3), files.data["song.flac"])
        assertArrayEquals(byteArrayOf(9, 8, 7), files.data["existing.flac"])
    }

    @Test fun failedCleanupIsRetainedAndRecoveredByANewSession() {
        val journal = Journal(); val files = Files().apply { refuseDelete = true }
        val error = runCatching { WifiUploadTransaction(journal, files).receive(upload(), 4, ByteArrayInputStream(byteArrayOf(1))) }.exceptionOrNull()
        assertEquals("cleanup_pending", error?.message)
        assertEquals(1, journal.pending.size)
        assertFalse(files.data.containsKey("song.flac"))
        files.refuseDelete = false
        WifiUploadTransaction(journal, files).recover("tree")
        assertTrue(journal.pending.isEmpty())
        assertEquals(setOf("existing.flac"), files.data.keys)
    }

    @Test fun cancellationAfterTheLastReadStillCleansBeforePublicationAndPreservesInterrupt() {
        val journal = Journal(); val files = Files()
        val input = object : InputStream() {
            override fun read(): Int = error("buffered read required")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                buffer[offset] = 1
                Thread.currentThread().interrupt()
                return 1
            }
        }
        try {
            assertTrue(runCatching { WifiUploadTransaction(journal, files).receive(upload(), 1, input) }.isFailure)
            assertTrue(Thread.currentThread().isInterrupted)
            assertEquals(setOf("existing.flac"), files.data.keys)
            assertTrue(journal.pending.isEmpty())
        } finally { Thread.interrupted() }
    }

    @Test fun recoveryCoversCreationGapButNeverDeletesAnAlreadyPublishedFileOrAnotherTree() {
        val journal = Journal(); val files = Files()
        val pending = upload()
        journal.put(pending) // Simulate death after create, before its document URI was journalled.
        files.data[pending.temporaryName] = byteArrayOf(1)
        val other = upload("other").copy(tree = "another-tree")
        journal.put(other); files.data[other.temporaryName] = byteArrayOf(2)
        val transfer = WifiUploadTransaction(journal, files)
        transfer.recover("tree")
        assertFalse(files.data.containsKey(pending.temporaryName))
        assertTrue(journal.pending.containsKey(other.id))
        journal.put(pending.copy(document = pending.temporaryName))
        files.data["song.flac"] = byteArrayOf(1, 2, 3) // Rename succeeded before the process died.
        transfer.recover("tree")
        assertArrayEquals(byteArrayOf(1, 2, 3), files.data["song.flac"])
        assertArrayEquals(byteArrayOf(9, 8, 7), files.data["existing.flac"])
    }
}
