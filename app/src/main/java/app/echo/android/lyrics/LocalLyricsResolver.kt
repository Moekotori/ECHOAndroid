package app.echo.android.lyrics

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import app.echo.android.data.LibraryTrackEntity
import app.echo.android.data.readCachedRemoteEmbeddedLyrics
import app.echo.android.model.library.CueSheetPolicy
import kotlinx.coroutines.CancellationException
import app.echo.android.R
import app.echo.android.model.lyrics.EchoLyrics
import java.io.File
import app.echo.android.model.settings.EchoLyricsSource
import app.echo.android.model.settings.EchoLyricsOptions

class LocalLyricsResolver(
    private val context: Context,
) {
    private val contentResolver: ContentResolver = context.contentResolver
    fun loadForTrack(track: LibraryTrackEntity, order: List<EchoLyricsSource> = EchoLyricsSource.entries.toList()): EchoLyrics? {
        val audioUri = CueSheetPolicy.playbackUri(track.contentUri)
        val fileName by lazy { track.fileName ?: safely { audioDisplayName(audioUri) } }
        val candidates by lazy { LyricsSidecarNames.candidates(fileName, track.title, track.clipStartMs > 0L || track.clipEndMs > 0L) }
        val sidecarReader = LocalLyricsSidecarReader(context)
        val sidecars by lazy { sidecarReader.list(track, Uri.parse(audioUri)) }
        val adaptSidecar: (EchoLyrics) -> EchoLyrics? = { LyricsClipPolicy.sidecar(it, fileName, track.clipStartMs, track.clipEndMs) }
        EchoLyricsOptions(sourceOrder = order).normalized.sourceOrder.forEach { source ->
            val result = if (source == EchoLyricsSource.Embedded) {
                safely { loadEmbeddedLyrics(track, audioUri)?.let { LyricsClipPolicy.embedded(it, track.clipStartMs, track.clipEndMs) } }
            } else {
                val names = candidates.filter { it.endsWith(".spl", true) == (source == EchoLyricsSource.Spl) }
                safely { sidecarReader.load(sidecars, names, adaptSidecar) }
            }
            if (result != null) return result
        }
        return null
    }

    fun loadFromUri(uri: Uri): EchoLyrics? {
        val sourceLabel = displayName(uri) ?: uri.lastPathSegment
        return readText(uri)
            ?.let { runCatching { it.attach(EchoLyricsParser.parse(it.text, sourceLabel = sourceLabel)) }.getOrNull() }
            ?.takeIf { it.lines.any { line -> line.text.isNotBlank() } }
    }

    fun importFromUri(uri: Uri, charsetName: String? = null): EchoLyrics {
        val sourceLabel = displayName(uri) ?: uri.lastPathSegment
        val text = readText(uri, charsetName)
            ?: throw IllegalArgumentException(context.getString(R.string.lyrics_read_failed))
        val lyrics = runCatching { text.attach(EchoLyricsParser.parse(text.text, sourceLabel = sourceLabel)) }
            .getOrElse { error ->
                throw IllegalArgumentException(
                    context.getString(R.string.lyrics_parse_failed, error.readableMessage()),
                    error,
                )
            }
        return lyrics
            .takeIf { it.lines.any { line -> line.text.isNotBlank() } }
            ?: throw IllegalArgumentException(context.getString(R.string.lyrics_file_empty))
    }

    private fun loadEmbeddedLyrics(track: LibraryTrackEntity, contentUri: String): EchoLyrics? {
        if (track.source.startsWith("smb:") || track.source.startsWith("webdav:")) {
            val text = readCachedRemoteEmbeddedLyrics(context.cacheDir, track) ?: return null
            return EmbeddedLyricsReader.parse(EmbeddedLyricsText(text, "Embedded tags"))
        }
        val uri = runCatching { Uri.parse(contentUri) }.getOrNull() ?: return null
        val embeddedText = runCatching {
            openLyricsInputStream(uri)?.use(EmbeddedLyricsReader::read)
        }.getOrNull() ?: return null

        return runCatching {
            EmbeddedLyricsReader.parse(embeddedText)
        }.getOrNull()
    }

    private fun readText(uri: Uri, charsetName: String? = null): EchoLyricsTextDecoder.Result? =
        runCatching {
            openLyricsInputStream(uri)?.use { input ->
                EchoLyricsTextDecoder.decodeResult(input.readLimitedLyricsBytes(), charsetName)?.takeUnless { it.lossy }
            }
        }.getOrNull()

    private fun openLyricsInputStream(uri: Uri) =
        runCatching { contentResolver.openInputStream(uri) }.getOrNull()
            ?: runCatching { contentResolver.openTypedAssetFileDescriptor(uri, "text/*", null)?.createInputStream() }
                .getOrNull()
            ?: runCatching { contentResolver.openTypedAssetFileDescriptor(uri, "*/*", null)?.createInputStream() }
                .getOrNull()

    private fun Throwable.readableMessage(): String =
        rootCause().let { root ->
            root.message?.takeIf { it.isNotBlank() }
                ?: root.javaClass.simpleName.takeIf { it.isNotBlank() }
        }
            ?: context.getString(R.string.unknown_error)

    private tailrec fun Throwable.rootCause(): Throwable =
        cause?.takeIf { it !== this }?.rootCause() ?: this

    private fun audioDisplayName(contentUri: String): String? {
        val uri = runCatching { Uri.parse(contentUri) }.getOrNull() ?: return null
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path?.let(::File)?.name
        }
        return contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                    ?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
    }

    private fun displayName(uri: Uri): String? =
        safely { contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else {
                null
            }
        } }

    private fun <T> safely(read: () -> T): T? = try { read() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }

}

private fun java.io.InputStream.readLimitedLyricsBytes(): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= 2 * 1024 * 1024) { "Lyrics file exceeds 2 MB" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
