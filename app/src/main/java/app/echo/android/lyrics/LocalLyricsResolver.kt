package app.echo.android.lyrics

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import app.echo.android.data.LibraryTrackEntity
import app.echo.android.model.lyrics.EchoLyrics
import java.io.File

class LocalLyricsResolver(
    private val contentResolver: ContentResolver,
) {
    fun loadForTrack(track: LibraryTrackEntity): EchoLyrics? {
        val candidates = buildCandidateNames(track)
        return loadFromFileUri(track.contentUri, candidates)
            ?: loadFromMediaStore(track, candidates)
    }

    fun loadFromUri(uri: Uri): EchoLyrics? {
        val sourceLabel = displayName(uri) ?: uri.lastPathSegment
        return readText(uri)
            ?.let { EchoLyricsParser.parse(it, sourceLabel = sourceLabel) }
    }

    fun importFromUri(uri: Uri): EchoLyrics {
        val sourceLabel = displayName(uri) ?: uri.lastPathSegment
        val text = readText(uri)
            ?: throw IllegalArgumentException("无法读取歌词文件，请确认文件权限或编码")
        val lyrics = runCatching { EchoLyricsParser.parse(text, sourceLabel = sourceLabel) }
            .getOrElse { error ->
                throw IllegalArgumentException("歌词解析失败：${error.readableMessage()}", error)
            }
        return lyrics
            .takeIf { it.lines.isNotEmpty() }
            ?: throw IllegalArgumentException("歌词文件为空，或不是支持的文本歌词格式")
    }

    private fun loadFromFileUri(contentUri: String, candidates: List<String>): EchoLyrics? {
        val uri = runCatching { Uri.parse(contentUri) }.getOrNull() ?: return null
        if (uri.scheme != ContentResolver.SCHEME_FILE) return null
        val audioFile = uri.path?.let(::File) ?: return null
        val parent = audioFile.parentFile ?: return null

        return candidates.asSequence()
            .map { File(parent, it) }
            .firstOrNull { it.isFile && it.canRead() }
            ?.let { file ->
                readText(file)?.let { text -> EchoLyricsParser.parse(text, sourceLabel = file.name) }
            }
    }

    private fun loadFromMediaStore(track: LibraryTrackEntity, candidates: List<String>): EchoLyrics? {
        val relativePath = track.relativePath?.takeIf { it.isNotBlank() } ?: return null
        if (candidates.isEmpty()) return null
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
        )
        val candidateLookup = candidates.associateBy { it.normalizedLyricsName() }
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
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val displayName = cursor.getString(nameIndex)
                    if (displayName.substringAfterLast('.', missingDelimiterValue = "")
                            .lowercase()
                            .let { ".$it" } !in LyricsExtensions
                    ) {
                        continue
                    }
                    if (displayName.normalizedLyricsName() !in candidateLookup) {
                        continue
                    }
                    val lyricsUri = Uri.withAppendedPath(collection, id.toString())
                    val parsed = readText(lyricsUri)
                        ?.let { EchoLyricsParser.parse(it, sourceLabel = displayName) }
                    if (parsed != null) return@use parsed
                }
                null
            }
    }

    private fun readText(uri: Uri): String? =
        runCatching {
            openLyricsInputStream(uri)?.use { input ->
                EchoLyricsTextDecoder.decode(input.readBytes())
            }
        }.getOrNull()

    private fun openLyricsInputStream(uri: Uri) =
        contentResolver.openInputStream(uri)
            ?: runCatching { contentResolver.openTypedAssetFileDescriptor(uri, "text/*", null)?.createInputStream() }
                .getOrNull()
            ?: runCatching { contentResolver.openTypedAssetFileDescriptor(uri, "*/*", null)?.createInputStream() }
                .getOrNull()

    private fun readText(file: File): String? =
        runCatching { EchoLyricsTextDecoder.decode(file.readBytes()) }.getOrNull()

    private fun Throwable.readableMessage(): String =
        rootCause().let { root ->
            root.message?.takeIf { it.isNotBlank() }
                ?: root.javaClass.simpleName.takeIf { it.isNotBlank() }
        }
            ?: "未知错误"

    private tailrec fun Throwable.rootCause(): Throwable =
        cause?.takeIf { it !== this }?.rootCause() ?: this

    private fun buildCandidateNames(track: LibraryTrackEntity): List<String> {
        val bases = linkedSetOf<String>()
        displayNameBase(track.contentUri)?.let(bases::add)
        track.title.takeIf { it.isNotBlank() }?.let(bases::add)

        return bases
            .flatMap { base -> LyricsExtensions.map { extension -> "$base$extension" } }
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
            .substringBeforeLast('.', missingDelimiterValue = this)
            .lowercase()
            .replace(Regex("""[\s._\-]+"""), "")

    private companion object {
        val LyricsExtensions = listOf(
            ".lrc",
            ".elrc",
            ".lrcx",
            ".yrc",
            ".ttml",
            ".srt",
            ".vtt",
            ".webvtt",
            ".ass",
            ".ssa",
            ".qrc",
            ".krc",
            ".txt",
        )
    }
}
