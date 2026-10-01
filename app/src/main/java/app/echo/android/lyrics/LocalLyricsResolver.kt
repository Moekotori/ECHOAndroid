package app.echo.android.lyrics

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import app.echo.android.data.LibraryTrackEntity
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
        val candidates by lazy { buildCandidateNames(track) }
        EchoLyricsOptions(sourceOrder = order).normalized.sourceOrder.forEach { source ->
            val result = if (source == EchoLyricsSource.Embedded) {
                loadEmbeddedLyrics(track.contentUri)
            } else {
                val names = candidates.filter { it.endsWith(".spl", true) == (source == EchoLyricsSource.Spl) }
                loadFromFileUri(track.contentUri, names) ?: loadFromMediaStore(track, names)
            }
            if (result != null) return result
        }
        return null
    }

    fun loadFromUri(uri: Uri): EchoLyrics? {
        val sourceLabel = displayName(uri) ?: uri.lastPathSegment
        return readText(uri)
            ?.let { runCatching { EchoLyricsParser.parse(it, sourceLabel = sourceLabel) }.getOrNull() }
            ?.takeIf { it.lines.any { line -> line.text.isNotBlank() } }
    }

    fun importFromUri(uri: Uri): EchoLyrics {
        val sourceLabel = displayName(uri) ?: uri.lastPathSegment
        val text = readText(uri)
            ?: throw IllegalArgumentException(context.getString(R.string.lyrics_read_failed))
        val lyrics = runCatching { EchoLyricsParser.parse(text, sourceLabel = sourceLabel) }
            .getOrElse { error ->
                throw IllegalArgumentException(
                    context.getString(R.string.lyrics_parse_failed, error.readableMessage()),
                    error,
                )
            }
        return lyrics
            .takeIf { it.lines.isNotEmpty() }
            ?: throw IllegalArgumentException(context.getString(R.string.lyrics_file_empty))
    }

    private fun loadFromFileUri(contentUri: String, candidates: List<String>): EchoLyrics? {
        val uri = runCatching { Uri.parse(contentUri) }.getOrNull() ?: return null
        if (uri.scheme != ContentResolver.SCHEME_FILE) return null
        val audioFile = uri.path?.let(::File) ?: return null
        val parent = audioFile.parentFile ?: return null

        return candidates.asSequence()
            .map { File(parent, it) }
            .filter { it.isFile && it.canRead() }
            .mapNotNull { file ->
                readText(file)?.let { text -> runCatching { EchoLyricsParser.parse(text, sourceLabel = file.name) }.getOrNull() }
                    ?.takeIf { it.lines.any { line -> line.text.isNotBlank() } }
            }
            .firstOrNull()
    }

    private fun loadEmbeddedLyrics(contentUri: String): EchoLyrics? {
        val uri = runCatching { Uri.parse(contentUri) }.getOrNull() ?: return null
        val embeddedText = runCatching {
            openLyricsInputStream(uri)?.use(EmbeddedLyricsReader::read)
        }.getOrNull() ?: return null

        return runCatching {
            EchoLyricsParser.parse(embeddedText.text, sourceLabel = embeddedText.sourceLabel)
        }.getOrNull()
            ?.takeIf { it.lines.isNotEmpty() }
    }

    private fun loadFromMediaStore(track: LibraryTrackEntity, candidates: List<String>): EchoLyrics? {
        val relativePath = track.relativePath?.takeIf { it.isNotBlank() } ?: return null
        if (candidates.isEmpty()) return null
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
        )
        val candidateLookup = candidates.mapIndexed { index, name -> name.normalizedLyricsName() to index }.toMap()
        val displayNamePlaceholders = candidates.joinToString(",") { "?" }
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ? AND " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        } else {
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} IN ($displayNamePlaceholders)"
        }
        val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(relativePath, "%.%")
        } else {
            candidates.toTypedArray()
        }

        return contentResolver.query(collection, projection, selection, selectionArgs, null)
            ?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val matches = linkedMapOf<Int, Uri>()
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val displayName = cursor.getString(nameIndex)
                    if (displayName.substringAfterLast('.', missingDelimiterValue = "")
                            .lowercase()
                            .let { ".$it" } !in LYRICS_EXTENSIONS
                    ) {
                        continue
                    }
                    if (displayName.normalizedLyricsName() !in candidateLookup) {
                        continue
                    }
                    val lyricsUri = Uri.withAppendedPath(collection, id.toString())
                    matches.putIfAbsent(candidateLookup.getValue(displayName.normalizedLyricsName()), lyricsUri)
                }
                matches.entries.sortedBy { it.key }.firstNotNullOfOrNull { (rank, uri) ->
                    readText(uri)?.let { runCatching { EchoLyricsParser.parse(it, sourceLabel = candidates[rank]) }.getOrNull() }
                        ?.takeIf { parsed -> parsed.lines.any { it.text.isNotBlank() } }
                }
            }
    }

    private fun readText(uri: Uri): String? =
        runCatching {
            openLyricsInputStream(uri)?.use { input ->
                EchoLyricsTextDecoder.decode(input.readLimitedLyricsBytes())
            }
        }.getOrNull()

    private fun openLyricsInputStream(uri: Uri) =
        contentResolver.openInputStream(uri)
            ?: runCatching { contentResolver.openTypedAssetFileDescriptor(uri, "text/*", null)?.createInputStream() }
                .getOrNull()
            ?: runCatching { contentResolver.openTypedAssetFileDescriptor(uri, "*/*", null)?.createInputStream() }
                .getOrNull()

    private fun readText(file: File): String? =
        runCatching { file.inputStream().use { EchoLyricsTextDecoder.decode(it.readLimitedLyricsBytes()) } }.getOrNull()

    private fun Throwable.readableMessage(): String =
        rootCause().let { root ->
            root.message?.takeIf { it.isNotBlank() }
                ?: root.javaClass.simpleName.takeIf { it.isNotBlank() }
        }
            ?: context.getString(R.string.unknown_error)

    private tailrec fun Throwable.rootCause(): Throwable =
        cause?.takeIf { it !== this }?.rootCause() ?: this

    private fun buildCandidateNames(track: LibraryTrackEntity): List<String> {
        val bases = linkedSetOf<String>()
        displayNameBase(track.contentUri)?.let(bases::add)
        track.title.takeIf { it.isNotBlank() }?.let(bases::add)

        return bases
            .flatMap { base -> LYRICS_EXTENSIONS.map { extension -> "$base$extension" } }
            .distinct()
    }

    private fun displayNameBase(contentUri: String): String? {
        val uri = runCatching { Uri.parse(contentUri) }.getOrNull() ?: return null
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path?.let(::File)?.nameWithoutExtension
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
                    ?.substringBeforeLast('.', missingDelimiterValue = "")
                    ?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
    }

    private fun displayName(uri: Uri): String? =
        contentResolver.query(
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
        }

    private fun String.normalizedLyricsName(): String =
        trim()
            .lowercase()
            .replace(Regex("""[\s._\-]+"""), "")

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
