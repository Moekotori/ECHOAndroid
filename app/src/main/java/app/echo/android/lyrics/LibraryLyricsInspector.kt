package app.echo.android.lyrics

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import app.echo.android.data.EchoLibraryRepository
import app.echo.android.data.LibraryLyricsInspectionTrack
import app.echo.android.data.libraryHealthStats
import app.echo.android.data.lyricsInspectionBatch
import app.echo.android.data.lyricsInspectionLastId
import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.LibraryLyricsInspection
import app.echo.android.model.lyrics.EchoLyrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/** Only runs on explicit request. One worker, 64 rows, eight small directory indexes. */
internal class LibraryLyricsInspector(
    private val context: Context,
    private val repository: EchoLibraryRepository,
    private val isPlaying: () -> Boolean,
    private val awaitPlaybackIdle: suspend () -> Unit,
) {
    private val inspectionMutex = Mutex()
    suspend fun inspect(report: (LibraryLyricsInspection) -> Unit) = inspectionMutex.withLock {
      withContext(Dispatchers.IO) {
        val jobContext = coroutineContext
        val store = ImportedLyricsStore(context)
        val session = Session(jobContext)
        var result = LibraryLyricsInspection(totalCount = repository.libraryHealthStats().trackCount)
        suspend fun waitForPlayback() {
            if (!isPlaying()) return
            withContext(Dispatchers.Main.immediate) { report(result.copy(waitingForPlayback = true)) }
            awaitPlaybackIdle()
            withContext(Dispatchers.Main.immediate) { report(result) }
        }
        waitForPlayback()
        val bindings = store.inspectionBindings()
        val lastId = repository.lyricsInspectionLastId().orEmpty()
        var after = ""
        var publishedAt = 0L
        while (true) {
            ensureActive()
            waitForPlayback()
            val rows = repository.lyricsInspectionBatch(after, lastId)
            if (rows.isEmpty()) break
            for (row in rows) {
                ensureActive()
                waitForPlayback()
                val outcome = session.inspect(row, store, bindings[row.id])
                result = when (outcome) {
                    Outcome.Found -> result.copy(foundCount = result.foundCount + 1)
                    Outcome.Missing -> result.copy(missingCount = result.missingCount + 1)
                    Outcome.Unverified -> result.copy(unverifiedCount = result.unverifiedCount + 1)
                }
                after = row.id
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - publishedAt >= 250L) {
                    withContext(Dispatchers.Main.immediate) { report(result) }
                    publishedAt = now
                }
                // Yield storage/CPU to playback; no parallel decoders or per-track UI ticks.
                delay(25L)
            }
        }
        result = result.copy(checkedAtEpochMs = System.currentTimeMillis())
        withContext(Dispatchers.Main.immediate) { report(result) }
      }
    }

    private enum class Outcome { Found, Missing, Unverified }
    private data class FolderIndex(val files: Map<String, Uri>, val complete: Boolean)

    private inner class Session(private val jobContext: CoroutineContext) {
        private val folders = object : LinkedHashMap<String, FolderIndex>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FolderIndex>?): Boolean = size > 8
        }
        private val resolver = context.contentResolver

        fun inspect(row: LibraryLyricsInspectionTrack, store: ImportedLyricsStore, binding: Uri?): Outcome {
            var failed = false
            fun attempt(read: () -> EchoLyrics?): EchoLyrics? = try {
                jobContext.ensureActive()
                read()?.takeIf { it.lines.any { line -> line.text.isNotBlank() } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
                null
            }
            if (attempt { store.readSaved(row.id, selected = true, touchLastAccess = false) } != null) return Outcome.Found
            if (binding != null && attempt { parseUri(binding) } != null) return Outcome.Found
            if (attempt {
                    store.readSaved(row.id, selected = false, touchLastAccess = false)?.takeIf {
                        OnlineLyricsCachePolicy.matches(it, EchoLyricsSearchRequest(row.title, row.artist, row.album, row.durationMs))
                    }
                } != null) return Outcome.Found

            val audioUri = Uri.parse(CueSheetPolicy.playbackUri(row.contentUri))
            val relativePath = row.relativePath
            val fileName = row.fileName ?: try {
                resolver.query(audioUri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
                    jobContext.ensureActive()
                    if (it.moveToFirst()) it.getString(0) else null
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true; null }
            if (fileName == null && audioUri.scheme != ContentResolver.SCHEME_FILE) failed = true
            val bases = listOfNotNull(
                fileName?.substringBeforeLast('.')?.takeIf(String::isNotBlank),
                if (audioUri.scheme == ContentResolver.SCHEME_FILE) File(audioUri.path.orEmpty()).nameWithoutExtension else null,
                row.title.takeIf(String::isNotBlank),
            ).distinct()
            val names = bases.flatMap { base -> EchoLyricsParser.fileExtensions.map { "$base$it" } }
            if (audioUri.scheme == ContentResolver.SCHEME_FILE) {
                val parent = File(audioUri.path.orEmpty()).parentFile
                if (parent == null || !parent.canRead()) failed = true
                names.forEach { name ->
                    val file = parent?.let { File(it, name) }
                    if (file?.isFile == true && attempt { parseUri(Uri.fromFile(file)) } != null) return Outcome.Found
                }
            } else if (audioUri.authority == MediaStore.AUTHORITY && !relativePath.isNullOrBlank() &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val folder = folderIndex(relativePath, audioUri.pathSegments.firstOrNull() ?: "external")
                    if (!folder.complete) failed = true
                    names.forEach { name ->
                        folder.files[normalize(name)]?.let { uri ->
                            if (attempt { parseUri(uri) } != null) return Outcome.Found
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) { failed = true }
            } else {
                // No accessible sibling directory (e.g. an isolated SAF document).
                failed = true
            }
            if (attempt {
                    val input = resolver.openInputStream(audioUri) ?: throw IOException("Audio is not readable")
                    input.use {
                        EmbeddedLyricsReader.read(BoundedInput(input, jobContext))?.let {
                            EchoLyricsParser.parse(it.text, sourceLabel = it.sourceLabel)
                        }
                    }
                } != null) return Outcome.Found
            return if (failed) Outcome.Unverified else Outcome.Missing
        }

        private fun parseUri(uri: Uri): EchoLyrics? = resolver.openInputStream(uri)?.use { input ->
            val bytes = BoundedInput(input, jobContext).readBytes()
            EchoLyricsTextDecoder.decode(bytes)?.let { EchoLyricsParser.parse(it) }
        } ?: throw IOException("Lyrics are not readable")

        private fun folderIndex(path: String, volume: String): FolderIndex = folders.getOrPut("$volume:$path") {
            val collection = MediaStore.Files.getContentUri(volume)
            val files = LinkedHashMap<String, Uri>()
            val extensions = EchoLyricsParser.fileExtensions
            val selection = "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ? AND (" +
                extensions.joinToString(" OR ") { "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?" } + ")"
            resolver.query(collection, arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DISPLAY_NAME),
                selection, (listOf(path) + extensions.map { "%$it" }).toTypedArray(), null)?.use { cursor ->
                var visited = 0
                while (cursor.moveToNext()) {
                    jobContext.ensureActive()
                    if (++visited > 512) return@getOrPut FolderIndex(files, false)
                    files.putIfAbsent(normalize(cursor.getString(1)), Uri.withAppendedPath(collection, cursor.getLong(0).toString()))
                }
            } ?: throw IOException("Folder is not readable")
            FolderIndex(files, true)
        }
    }

    private class BoundedInput(input: InputStream, private val jobContext: CoroutineContext) : FilterInputStream(input) {
        private var remaining = 2 * 1024 * 1024
        override fun read(): Int {
            jobContext.ensureActive()
            if (remaining <= 0) throw IOException("Inspection byte limit")
            return `in`.read().also { if (it >= 0) remaining-- }
        }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            jobContext.ensureActive()
            if (length == 0) return 0
            if (remaining <= 0) throw IOException("Inspection byte limit")
            return `in`.read(bytes, offset, minOf(length, remaining)).also { if (it > 0) remaining -= it }
        }
        override fun skip(count: Long): Long {
            jobContext.ensureActive()
            if (count <= 0L) return 0L
            if (count > remaining) throw IOException("Inspection byte limit")
            return `in`.skip(count).also { remaining -= it.toInt() }
        }
    }

    private fun normalize(name: String): String = name.trim().lowercase(java.util.Locale.ROOT)
        .replace(NameSeparators, "")

    private companion object { val NameSeparators = Regex("[\\s._\\-]+") }
}
