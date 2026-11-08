package app.echo.android.lyrics

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import app.echo.android.data.EchoLibraryRepository
import app.echo.android.data.LibraryLyricsInspectionTrack
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.LibraryLyricsIndex
import app.echo.android.data.LibraryLyricsInspectionResult
import app.echo.android.data.DocumentSiblingFiles
import app.echo.android.data.libraryHealthStats
import app.echo.android.data.lyricsInspectionBatch
import app.echo.android.data.lyricsInspectionLastId
import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.LibraryLyricsInspection
import app.echo.android.model.lyrics.EchoLyrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
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
        val index = LibraryLyricsIndex(EchoLibraryDatabase.create(context.applicationContext))
        val pending = ArrayList<LibraryLyricsInspectionResult>(64)
        suspend fun flush() {
            if (pending.isEmpty()) return
            val completed = pending.toList()
            pending.clear()
            index.recordInspection(completed)
        }
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
        try { while (true) {
            ensureActive()
            waitForPlayback()
            val rows = repository.lyricsInspectionBatch(after, lastId)
            if (rows.isEmpty()) break
            for (row in rows) {
                ensureActive()
                waitForPlayback()
                val inspectedAt = System.currentTimeMillis()
                val outcome = session.inspect(row, store, bindings[row.id])
                pending += LibraryLyricsInspectionResult(row, when (outcome) {
                    Outcome.Found -> true
                    Outcome.Missing -> false
                    Outcome.Unverified -> null
                }, inspectedAt)
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
            flush()
        } } finally {
            // At most 64 completed rows; cancellation does not discard already verified work.
            withContext(NonCancellable) {
                flush()
                withContext(Dispatchers.Main.immediate) { report(result.copy(waitingForPlayback = false)) }
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
                LyricsContentPolicy.readable(read())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
                null
            }
            if (attempt { store.readSaved(row.id, selected = true, touchLastAccess = false, reportReadFailure = true) } != null) return Outcome.Found
            if (binding != null && attempt { parseUri(binding) } != null) return Outcome.Found
            if (attempt {
                    store.readSaved(row.id, selected = false, touchLastAccess = false, reportReadFailure = true)?.takeIf {
                        OnlineLyricsCachePolicy.matches(it, EchoLyricsSearchRequest(row.title, row.artist, row.album, row.durationMs))
                    }
                } != null) return Outcome.Found

            val audioUri = Uri.parse(CueSheetPolicy.playbackUri(row.contentUri))
            val relativePath = row.relativePath
            val fileName = row.fileName ?: if (audioUri.scheme == ContentResolver.SCHEME_FILE) File(audioUri.path.orEmpty()).name else try {
                resolver.query(audioUri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
                    jobContext.ensureActive()
                    if (it.moveToFirst()) it.getString(0) else null
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true; null }
            if (fileName == null && audioUri.scheme != ContentResolver.SCHEME_FILE) failed = true
            val clipped = row.clipStartMs > 0L || row.clipEndMs > 0L
            val names = LyricsSidecarNames.candidates(fileName, row.title, preferTitle = clipped)
            fun sidecar(uri: Uri, name: String? = null): EchoLyrics? =
                LyricsClipPolicy.sidecar(parseUri(uri, name), fileName, row.clipStartMs, row.clipEndMs)
            try {
                val directory = when {
                    audioUri.scheme == ContentResolver.SCHEME_FILE -> "file:${File(audioUri.path.orEmpty()).parent}"
                    audioUri.authority == MediaStore.AUTHORITY -> "media:$relativePath"
                    else -> "saf:${DocumentSiblingFiles.directoryKey(audioUri) ?: audioUri}"
                }
                val folder = folders.getOrPut(directory) {
                    val listing = LocalLyricsSidecarReader(context) { jobContext.ensureActive() }.list(audioUri, relativePath)
                    FolderIndex(listing.files.entries.take(512).associate { it.key to it.value }, listing.complete && listing.files.size <= 512)
                }
                if (!folder.complete) failed = true
                LyricsSidecarDiscovery.discover(names, folder.files.keys, folder.complete).forEach { candidate ->
                    jobContext.ensureActive()
                    val uri = folder.files.getValue(candidate.primary)
                    if (attempt { sidecar(uri, candidate.primary) } != null) return Outcome.Found
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            if (attempt {
                    val input = resolver.openInputStream(audioUri) ?: throw IOException("Audio is not readable")
                    input.use {
                        EmbeddedLyricsReader.readForInspection(input) { jobContext.ensureActive() }
                            ?.let(EmbeddedLyricsReader::parse)
                            ?.let { LyricsClipPolicy.embedded(it, row.clipStartMs, row.clipEndMs) }
                    }
                } != null) return Outcome.Found
            return if (failed) Outcome.Unverified else Outcome.Missing
        }

        private fun parseUri(uri: Uri, sourceLabel: String? = null): EchoLyrics = resolver.openInputStream(uri)?.use { input ->
            val bytes = BoundedInput(input, jobContext).readBytes()
            val label = sourceLabel ?: if (uri.scheme == ContentResolver.SCHEME_FILE) uri.lastPathSegment else try {
                resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            EchoLyricsTextDecoder.decodeResult(bytes)?.takeUnless { it.lossy }?.let { EchoLyricsParser.parse(it.text, sourceLabel = label) }
        } ?: throw IOException("Lyrics are not readable")

    }

    internal class BoundedInput(input: InputStream, private val jobContext: CoroutineContext) : FilterInputStream(input) {
        private var remaining = 2 * 1024 * 1024
        override fun read(): Int {
            jobContext.ensureActive()
            if (remaining <= 0) return readLimitEnd()
            return `in`.read().also { if (it >= 0) remaining-- }
        }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            jobContext.ensureActive()
            if (length == 0) return 0
            if (remaining <= 0) return readLimitEnd()
            return `in`.read(bytes, offset, minOf(length, remaining)).also { if (it > 0) remaining -= it }
        }
        override fun skip(count: Long): Long {
            jobContext.ensureActive()
            if (count <= 0L) return 0L
            if (count > remaining) throw IOException("Inspection byte limit")
            return `in`.skip(count).also { remaining -= it.toInt() }
        }

        private fun readLimitEnd(): Int {
            if (`in`.read() < 0) return -1
            throw IOException("Inspection byte limit")
        }
    }

}
