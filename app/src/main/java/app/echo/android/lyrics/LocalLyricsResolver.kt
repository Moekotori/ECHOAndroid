package app.echo.android.lyrics

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import app.echo.android.data.LibraryTrackEntity
import app.echo.android.data.DocumentSiblingFiles
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
        val adaptSidecar: (EchoLyrics) -> EchoLyrics? = { LyricsClipPolicy.sidecar(it, fileName, track.clipStartMs, track.clipEndMs) }
        EchoLyricsOptions(sourceOrder = order).normalized.sourceOrder.forEach { source ->
            val result = if (source == EchoLyricsSource.Embedded) {
                safely { loadEmbeddedLyrics(track, audioUri)?.let { LyricsClipPolicy.embedded(it, track.clipStartMs, track.clipEndMs) } }
            } else {
                val names = candidates.filter { it.endsWith(".spl", true) == (source == EchoLyricsSource.Spl) }
                safely { loadFromFileUri(audioUri, names, adaptSidecar, track.clipStartMs > 0L || track.clipEndMs > 0L) } ?:
                    safely { loadFromDocumentTree(audioUri, names, adaptSidecar) } ?:
                    safely { loadFromMediaStore(track, names, adaptSidecar) }
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

    private fun loadFromFileUri(contentUri: String, candidates: List<String>, adapt: (EchoLyrics) -> EchoLyrics?, rankBeforeRead: Boolean): EchoLyrics? {
        val uri = runCatching { Uri.parse(contentUri) }.getOrNull() ?: return null
        if (uri.scheme != ContentResolver.SCHEME_FILE) return null
        val audioFile = uri.path?.let(::File) ?: return null
        val parent = audioFile.parentFile ?: return null

        val matches = sequence {
            if (!rankBeforeRead) yieldAll(candidates)
            var complete = true
            val files = java.nio.file.Files.newDirectoryStream(parent.toPath()).use { paths ->
                paths.asSequence().take(4097).mapIndexedNotNull { index, path ->
                    if (index == 4096) { complete = false; null } else path
                }.filter { java.nio.file.Files.isRegularFile(it) }
                    .map { it.fileName.toString() }
                    .filter { name -> LYRICS_EXTENSIONS.any { name.endsWith(it, true) } }.toList()
            }
            val ranked = LyricsSidecarNames.matching(candidates, files, complete)
            yieldAll(if (rankBeforeRead) ranked else ranked.filter { it !in candidates })
            if (rankBeforeRead) yieldAll(candidates.filter { it !in ranked })
        }
        return matches
            .map { File(parent, it) }
            .filter { it.isFile && it.canRead() }
            .mapNotNull { file ->
                readText(file)?.let { text -> runCatching { text.attach(EchoLyricsParser.parse(text.text, sourceLabel = file.name)) }.getOrNull() }
                    ?.let(adapt)
                    ?.takeIf { it.lines.any { line -> line.text.isNotBlank() } }
            }
            .firstOrNull()
    }

    private fun loadFromDocumentTree(contentUri: String, candidates: List<String>, adapt: (EchoLyrics) -> EchoLyrics?): EchoLyrics? {
        val listing = DocumentSiblingFiles.read(contentResolver, Uri.parse(contentUri), LYRICS_EXTENSIONS) ?: return null
        val byName = listing.files.associateBy { it.name }
        return LyricsSidecarNames.matching(candidates, byName.keys, listing.complete).firstNotNullOfOrNull { name ->
            readText(byName.getValue(name).uri)?.let { text ->
                safely { text.attach(EchoLyricsParser.parse(text.text, sourceLabel = name)) }
            }?.let(adapt)?.takeIf { it.lines.any { line -> line.text.isNotBlank() } }
        }
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

    private fun loadFromMediaStore(track: LibraryTrackEntity, candidates: List<String>, adapt: (EchoLyrics) -> EchoLyrics?): EchoLyrics? {
        if (Uri.parse(CueSheetPolicy.playbackUri(track.contentUri)).authority != MediaStore.AUTHORITY) return null
        val location = LyricsSidecarLocation.fromLibraryFolder(track.relativePath) ?: return null
        if (candidates.isEmpty()) return null
        val modernStorage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val collection = MediaStore.Files.getContentUri(if (modernStorage) location.volumeName else "external")
        @Suppress("DEPRECATION")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
        ) + if (modernStorage) emptyArray() else arrayOf(MediaStore.MediaColumns.DATA)
        @Suppress("DEPRECATION")
        val primaryStorageRoot = if (modernStorage) "" else Environment.getExternalStorageDirectory().absolutePath
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ? AND " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        } else {
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} COLLATE NOCASE IN (${candidates.joinToString(",") { "?" }})"
        }
        val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(location.relativePath, "%.%")
        } else {
            candidates.toTypedArray()
        }

        return contentResolver.query(collection, projection, selection, selectionArgs, null)
            ?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                @Suppress("DEPRECATION")
                val dataIndex = if (modernStorage) -1 else cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val matches = linkedMapOf<String, Uri>()
                var visited = 0
                var complete = true
                while (cursor.moveToNext()) {
                    if (++visited > 4096) { complete = false; break }
                    if (!modernStorage && !location.containsLegacyFile(cursor.getString(dataIndex), primaryStorageRoot)) continue
                    val id = cursor.getLong(idIndex)
                    val displayName = cursor.getString(nameIndex)
                    if (displayName.substringAfterLast('.', missingDelimiterValue = "")
                            .lowercase()
                            .let { ".$it" } !in LYRICS_EXTENSIONS
                    ) {
                        continue
                    }
                    val lyricsUri = Uri.withAppendedPath(collection, id.toString())
                    matches.putIfAbsent(displayName, lyricsUri)
                }
                LyricsSidecarNames.matching(candidates, matches.keys, complete).firstNotNullOfOrNull { name ->
                    readText(matches.getValue(name))?.let { runCatching { it.attach(EchoLyricsParser.parse(it.text, sourceLabel = name)) }.getOrNull() }
                        ?.let(adapt)
                        ?.takeIf { parsed -> parsed.lines.any { it.text.isNotBlank() } }
                }
            }
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

    private fun readText(file: File): EchoLyricsTextDecoder.Result? =
        runCatching { file.inputStream().use { EchoLyricsTextDecoder.decodeResult(it.readLimitedLyricsBytes())?.takeUnless { it.lossy } } }.getOrNull()

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

    private companion object {
        val LYRICS_EXTENSIONS = EchoLyricsParser.fileExtensions
    }
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
