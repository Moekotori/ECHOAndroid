package app.echo.android.data

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

    private fun indexedTrack() = LibraryTrackEntity(
        id = "mediastore:1", contentUri = "content://media/external/audio/media/1",
        title = "first", artist = "Artist", album = null, albumArtist = null, artworkUri = null,
        durationMs = 1000L, trackNumber = null, discNumber = null, year = null,
        mimeType = "audio/wav", sizeBytes = 1024L, dateModifiedSeconds = 1700000000L,
        relativePath = "Music/",
    )

    private class ListingProvider(private val names: List<String>, private val fail: Boolean = false) : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            if (fail) return null
            return MatrixCursor(projection!!).apply {
                names.forEach { name ->
                    addRow(projection.map { column ->
                        when (column) {
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID -> "primary:Music/$name"
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME -> name
                            DocumentsContract.Document.COLUMN_MIME_TYPE -> "audio/wav"
                            DocumentsContract.Document.COLUMN_SIZE -> 1024L
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED -> 1700000000000L
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
