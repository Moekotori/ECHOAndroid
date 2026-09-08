package app.echo.android.data

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import app.echo.android.model.library.LibraryScanOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class MediaStoreIncrementalScanAndroidTest {
    @Test fun secondFilteredScanUsesOnlyChangeProbeEvenWithEmptyLibrary() = runBlocking {
        val provider = AudioProvider()
        val resolver = ContentResolver.wrap(provider)
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getContentResolver(): ContentResolver = resolver
        }
        val scanner = MediaStoreTrackScanner(context)
        val cache = LocalScanFilterCache()
        val tracks = mutableListOf<LibraryTrackEntity>()
        val options = LibraryScanOptions()
        suspend fun scan(filters: LibraryScanOptions) = scanner.scanAudio(relativePathPrefix = "Music/",
            options = filters, rejectedFiles = cache, onBatch = { tracks += it }, onProgress = { _, _ -> })
        assertEquals(1, scan(options).scannedCount)
        assertEquals(1, scan(options).scannedCount)
        assertEquals(1, provider.fullQueries)
        assertEquals(1, provider.probes)
        assertEquals(0, tracks.size)
        scan(options.copy(minDurationMs = 0))
        assertEquals(2, provider.fullQueries)
        assertEquals(1, tracks.size)
    }

    private class AudioProvider : ContentProvider() {
        var fullQueries = 0
        var probes = 0
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            val columns = projection!!
            if (MediaStore.Audio.Media.TITLE in columns) fullQueries++ else probes++
            return MatrixCursor(columns).apply {
                addRow(columns.map { column -> when (column) {
                    MediaStore.Audio.Media._ID -> 1L
                    MediaStore.Audio.Media.DATE_MODIFIED -> 100L
                    MediaStore.Audio.Media.SIZE -> 500_000L
                    MediaStore.Audio.Media.DURATION -> 1000L
                    MediaStore.Audio.Media.RELATIVE_PATH -> "Music/"
                    MediaStore.MediaColumns.VOLUME_NAME -> "external_primary"
                    MediaStore.Audio.Media.TITLE -> "Short clip"
                    MediaStore.Audio.Media.ARTIST -> "Artist"
                    MediaStore.Audio.Media.MIME_TYPE -> "audio/wav"
                    "sample_rate" -> 44_100
                    else -> null
                } })
            }
        }
        override fun getType(uri: Uri) = "audio/wav"
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}
