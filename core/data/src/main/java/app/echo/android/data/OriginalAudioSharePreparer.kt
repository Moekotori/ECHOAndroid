package app.echo.android.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.CueSheetPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

class OriginalAudioShareReadException(val trackTitle: String, cause: Exception) : IOException(cause)
class OriginalAudioShareCacheFullException : IOException()

class PreparedOriginalAudioShare internal constructor(
    val uris: List<Uri>,
    val mimeType: String,
    val missingLyrics: Int,
    val missingCovers: Int,
    private val directory: File,
    private val createdAtMs: Long,
) {
    val expired: Boolean get() = System.currentTimeMillis() - createdAtMs > OriginalAudioShareStore.RetentionMs
    fun intent(): Intent = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = mimeType
        if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.single())
        else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        clipData = ClipData.newRawUri("", uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    suspend fun discard() = withContext(Dispatchers.IO) { directory.deleteRecursively(); Unit }
}

/** Sequential preparation; originals are opened for verification, never decoded or copied. */
class OriginalAudioSharePreparer(context: Context) {
    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val root = File(app.cacheDir, "original-audio-shares")

    suspend fun prepare(
        tracks: List<EchoTrack>,
        includeLyrics: Boolean,
        includeCovers: Boolean,
        loadLyrics: suspend (EchoTrack) -> OriginalAudioShareLyrics?,
        onProgress: suspend (Int) -> Unit,
    ): PreparedOriginalAudioShare = withContext(Dispatchers.IO) {
        gate.withLock {
            require(tracks.size in 1..OriginalAudioSharePolicy.MaxTracks && tracks.all(OriginalAudioSharePolicy::supports))
            root.mkdirs()
            val now = System.currentTimeMillis()
            root.listFiles().orEmpty().filter { now - it.lastModified() > RetentionMs }.forEach { it.deleteRecursively() }
            val sessions = root.listFiles().orEmpty()
            var usedBytes = sessions.sumOf { directory -> directory.walkTopDown().filter(File::isFile).sumOf(File::length) }
            if (sessions.size >= MaxSessions || usedBytes >= MaxCacheBytes) throw OriginalAudioShareCacheFullException()
            val directory = File(root, UUID.randomUUID().toString()).apply { if (!mkdir()) throw IOException() }
            try {
                val entries = JSONArray()
                val uris = ArrayList<Uri>()
                val mimes = ArrayList<String>()
                val names = OriginalAudioShareNames()
                val audioByUri = HashMap<String, String>()
                val sharedCovers = HashSet<Pair<String, String?>>()
                var missingLyrics = 0
                var missingCovers = 0
                fun add(source: Uri, name: String, mime: String, size: Long) {
                    val index = entries.length()
                    entries.put(JSONObject().put("source", source.toString()).put("name", name).put("mime", mime).put("size", size))
                    uris += Uri.Builder().scheme("content").authority("${app.packageName}.audio-share")
                        .appendPath(directory.name).appendPath(index.toString()).appendPath(name).build()
                    mimes += mime
                }
                fun attachment(bytes: ByteArray, wanted: String, mime: String) {
                    if (bytes.size > MaxCacheBytes - ManifestReserveBytes - usedBytes) throw OriginalAudioShareCacheFullException()
                    val name = names.attachment(wanted)
                    val file = File(directory, name).apply { writeBytes(bytes) }
                    usedBytes += bytes.size
                    add(Uri.fromFile(file), name, mime, file.length())
                }
                tracks.forEachIndexed { index, track ->
                    currentCoroutineContext().ensureActive()
                    val original = OriginalAudioSharePolicy.originalUri(track)
                    val uri = original.toUri()
                    var audioName = audioByUri[original]
                    if (audioName == null) {
                        try {
                            var size = track.sizeBytes.takeIf { it > 0 } ?: -1L
                            var name = track.fileName
                            resolver.openFileDescriptor(uri, "r")?.use { if (it.statSize >= 0) size = it.statSize }
                                ?: throw IOException()
                            if (uri.scheme == "content") optional { resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !cursor.isNull(it) }?.let { name = cursor.getString(it) }
                                    cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }?.let { size = cursor.getLong(it) }
                                }
                            } }
                            val type = track.mimeType?.takeUnless { it == "application/octet-stream" }
                                ?: optional { resolver.getType(uri) }
                            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(type)
                            audioName = names.audio(name ?: uri.lastPathSegment?.takeIf { '.' in it }
                                ?: "${track.title}.${extension ?: "audio"}")
                            val mime = type ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(audioName.substringAfterLast('.').lowercase()) ?: "application/octet-stream"
                            add(uri, audioName, mime, size)
                            audioByUri[original] = audioName
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { throw OriginalAudioShareReadException(track.title, failure) }
                    }
                    val stem = audioName.substringBeforeLast('.', audioName)
                    // CUE tracks share one original, but keep each track's own lyric attachment.
                    val lyricStem = if (CueSheetPolicy.isCueTrackId(track.id) || track.clipStartMs > 0 || track.clipEndMs > 0)
                        "$stem - ${OriginalAudioSharePolicy.safeName(track.title)}" else stem
                    if (includeLyrics) {
                        val lyrics = optional { loadLyrics(track) }
                        val bytes = lyrics?.text?.takeIf(String::isNotBlank)?.toByteArray(Charsets.UTF_8)?.takeIf { it.size <= MaxLyricsBytes }
                        if (bytes == null || lyrics.extension !in setOf("lrc", "txt", "json")) missingLyrics++
                        else {
                            attachment(bytes, "$lyricStem.${lyrics.extension}", if (lyrics.extension == "json") "application/json" else "text/plain")
                            lyrics.completeJson?.toByteArray(Charsets.UTF_8)?.let { complete ->
                                if (complete.size <= MaxLyricsBytes) attachment(complete, "$lyricStem.echo-lyrics.json", "application/json")
                                else missingLyrics++
                            }
                        }
                    }
                    if (includeCovers && sharedCovers.add(original to track.artworkUri)) {
                        val cover = optional { cover(track, uri) }
                        if (cover == null) missingCovers++ else attachment(cover.first, "$stem.${cover.second}", "image/${if (cover.second == "jpg") "jpeg" else cover.second}")
                    }
                    onProgress(index + 1)
                }
                currentCoroutineContext().ensureActive()
                val createdAt = System.currentTimeMillis()
                val manifest = JSONObject().put("createdAt", createdAt).put("files", entries).toString().toByteArray(Charsets.UTF_8)
                if (manifest.size > ManifestReserveBytes || manifest.size > MaxCacheBytes - usedBytes)
                    throw OriginalAudioShareCacheFullException()
                File(directory, "manifest.json").writeBytes(manifest)
                directory.setLastModified(System.currentTimeMillis())
                val types = mimes.distinct()
                val mime = when {
                    types.size == 1 -> types.single()
                    types.all { it.startsWith("audio/") } -> "audio/*"
                    else -> "*/*"
                }
                PreparedOriginalAudioShare(uris, mime, missingLyrics, missingCovers, directory, createdAt)
            } catch (failure: Throwable) {
                directory.deleteRecursively()
                throw failure
            }
        }
    }

    private suspend fun cover(track: EchoTrack, audioUri: Uri): Pair<ByteArray, String>? {
        val source = track.artworkUri?.toUri()?.takeIf { it.scheme in setOf("content", "file") }
        var bytes = optional { source?.let { resolver.openInputStream(it)?.use { input -> readArtworkSource(input, MaxCoverBytes) } } }
        if (bytes == null) bytes = optional { resolver.openInputStream(audioUri)?.use { readLocalAudioTags(it)?.artworkBytes } }
        val image = bytes?.takeIf { it.isNotEmpty() && it.size <= MaxCoverBytes } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image, 0, image.size, bounds)
        val extension = when (bounds.outMimeType) { "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> return null }
        return image to extension
    }

    private suspend fun <T> optional(block: suspend () -> T): T? = try { block() }
        catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }

    companion object {
        internal const val RetentionMs = OriginalAudioShareStore.RetentionMs
        private const val MaxCacheBytes = 64L * 1024 * 1024
        private const val MaxSessions = 64
        private const val ManifestReserveBytes = OriginalAudioShareStore.MaxManifestBytes
        private const val MaxCoverBytes = 8 * 1024 * 1024
        private const val MaxLyricsBytes = 2 * 1024 * 1024
        private val gate = Mutex()
    }
}
