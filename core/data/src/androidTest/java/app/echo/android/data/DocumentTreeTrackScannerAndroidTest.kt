package app.echo.android.data

import app.echo.android.model.library.LibraryScanOptions
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class DocumentTreeTrackScannerAndroidTest {
    private val tree = DocumentsContract.buildTreeDocumentUri("echo.scan.test", "primary:Music")

    @Test
    fun equalSizeAndTimestampDoNotHideAnotherFilename() = runBlocking {
        val scanner = DocumentTreeTrackScanner(ContentResolver.wrap(ListingProvider(listOf("first.wav", "second.wav"))))
        val tracks = mutableListOf<LibraryTrackEntity>()
        val key = LibraryScanPolicy.localFileDuplicateKey("Music/", 1024L, 1700000000L, "first.wav")!!
        val outcome = scanner.scanAudioTree(
            tree, "Music/", mediaStoreDuplicateKeys = mapOf(key to indexedTrack()),
            onBatch = { tracks += it }, onProgress = { _, _ -> },
        )
        assertTrue(outcome.querySucceeded)
        assertEquals(2, outcome.scannedCount)
        assertEquals(listOf("mediastore:1", "saf:${Uri.encode("primary:Music/second.wav")}"), tracks.map { it.id })
        assertEquals(listOf("first", "second"), tracks.map { it.title })
        assertTrue(tracks.all { it.relativePath == "Music/" })
    }

    @Test
    fun emptyDirectoryAndFailedListingHaveDifferentCompleteness() = runBlocking {
        suspend fun scan(fail: Boolean) = DocumentTreeTrackScanner(
            ContentResolver.wrap(ListingProvider(emptyList(), fail)),
        ).scanAudioTree(tree, "Music/", onBatch = {}, onProgress = { _, _ -> })
        assertTrue(scan(false).querySucceeded)
        assertFalse(scan(true).querySucceeded)
    }

    @Test
    fun failedMetadataIsRetriedWithUnchangedFileFingerprint() = runBlocking {
        val provider = ListingProvider(listOf("retry.wav"))
        provider.failReads = true
        val scanner = DocumentTreeTrackScanner(ContentResolver.wrap(provider))
        val tracks = mutableListOf<LibraryTrackEntity>()
        val first = scanner.scanAudioTree(tree, "Music/", onBatch = { tracks += it }, onProgress = { _, _ -> })
        assertFalse(first.querySucceeded)
        assertEquals(1, first.failedReadCount)
        val pending = tracks.single().withScanMetadata()
        assertEquals(LibraryScanPolicy.PendingDocumentMetadataFingerprint, pending.fingerprint)
        provider.failReads = false
        tracks.clear()
        val second = scanner.scanAudioTree(tree, "Music/", existingTracks = mapOf(pending.id to TrackFingerprint(
            pending.id, pending.contentUri, pending.sampleRateHz, pending.fingerprint,
            pending.sizeBytes, pending.dateModifiedSeconds, pending.relativePath, pending.durationMs,
        )), onBatch = { tracks += it }, onProgress = { _, _ -> })
        assertTrue(second.querySucceeded)
        assertEquals(2, provider.opens)
        assertTrue(tracks.single().durationMs > 0)
        assertTrue(tracks.single().fingerprint != LibraryScanPolicy.PendingDocumentMetadataFingerprint)
    }

    @Test
    fun excludedDirectoryIsNeverQueriedOrOpened() = runBlocking {
        val provider = ListingProvider(listOf("Recordings"))
        val scanner = DocumentTreeTrackScanner(ContentResolver.wrap(provider))
        val outcome = scanner.scanAudioTree(tree, "Removable/abcd/Music/", options = LibraryScanOptions(),
            onBatch = { error("Excluded directory must not yield tracks") }, onProgress = { _, _ -> })
        assertTrue(outcome.querySucceeded)
        assertEquals(1, outcome.excludedDirectoryCount)
        assertEquals(1, provider.queries)
        assertEquals(0, provider.opens)
    }

    @Test
    fun repeatedFilteredScanDoesNotReopenUnchangedAudio() = runBlocking {
        val provider = ListingProvider(listOf("short.wav"))
        val scanner = DocumentTreeTrackScanner(ContentResolver.wrap(provider))
        val cache = LocalScanFilterCache()
        val tracks = mutableListOf<LibraryTrackEntity>()
        var skipped = 0
        suspend fun scan(options: LibraryScanOptions) = scanner.scanAudioTree(tree, "Music/",
            options = options, rejectedFiles = cache, onSkipped = { skipped++ },
            onBatch = { tracks += it }, onProgress = { _, _ -> })
        val filters = LibraryScanOptions(minSizeBytes = 0)
        assertTrue(scan(filters).querySucceeded)
        assertEquals(1, provider.opens)
        assertEquals(1, skipped)
        assertTrue(tracks.isEmpty())
        assertTrue(scan(filters).querySucceeded)
        assertEquals(1, provider.opens) // Second scan lists the directory but opens no audio files.
        assertEquals(2, skipped)
        provider.modified += 1000L
        scan(filters)
        assertEquals(2, provider.opens)
        scan(filters.copy(minDurationMs = 0))
        assertEquals(3, provider.opens)
        assertEquals(1, tracks.size)
    }

    private fun indexedTrack() = LibraryTrackEntity(
        id = "mediastore:1", contentUri = "content://media/external/audio/media/1",
        title = "first", artist = "Artist", album = null, albumArtist = null, artworkUri = null,
        durationMs = 1000L, trackNumber = null, discNumber = null, year = null,
        mimeType = "audio/wav", sizeBytes = 1024L, dateModifiedSeconds = 1700000000L,
        relativePath = "Music/",
    )

    private class ListingProvider(private val names: List<String>, private val fail: Boolean = false) : ContentProvider() {
        var failReads = false
        var opens = 0
        var modified = 1700000000000L
        var queries = 0
        private val wav by lazy {
            File.createTempFile("scan-fixture", ".wav", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir).apply {
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                    .put("RIFF".toByteArray()).putInt(16036).put("WAVEfmt ".toByteArray())
                    .putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000)
                    .putShort(2).putShort(16).put("data".toByteArray()).putInt(16000).array()
                writeBytes(header + ByteArray(16000))
            }
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            opens++
            if (failReads) throw FileNotFoundException("Temporary SD read failure")
            return ParcelFileDescriptor.open(wav, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            queries++
            if (fail) return null
            return MatrixCursor(projection!!).apply {
                names.forEach { name ->
                    addRow(projection.map { column ->
                        when (column) {
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID -> "primary:Music/$name"
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME -> name
                            DocumentsContract.Document.COLUMN_MIME_TYPE -> if (name == "Recordings") DocumentsContract.Document.MIME_TYPE_DIR else "audio/wav"
                            DocumentsContract.Document.COLUMN_SIZE -> 1024L
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED -> modified
                            else -> null
                        }
                    })
                }
            }
        }
        override fun getType(uri: Uri): String = "audio/wav"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}
