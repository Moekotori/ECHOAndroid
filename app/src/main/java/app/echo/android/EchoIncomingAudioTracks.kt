package app.echo.android

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import app.echo.android.data.EchoLibraryRepository
import app.echo.android.data.toEchoTrack
import app.echo.android.model.i18n.echoText
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibrarySource

fun tryTakePersistableReadPermission(context: Context, uri: Uri) {
    if (uri.scheme != "content") return
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

suspend fun resolveIncomingAudioTrack(
    context: Context,
    repository: EchoLibraryRepository,
    uri: Uri,
): EchoTrack? {
    val content = uri.toString()
    if (content.isBlank()) return null
    EchoIncomingAudio.libraryTrackIdForMediaStoreUri(uri)?.let { trackId ->
        repository.trackById(trackId)?.toEchoTrack()?.let { return it }
    }
    repository.trackByContentUri(content)?.toEchoTrack()?.let { return it }
    return readStandaloneIncomingTrack(context, uri)
}

private fun readStandaloneIncomingTrack(context: Context, uri: Uri): EchoTrack {
    val fallbackTitle = displayName(context, uri)
        ?.substringBeforeLast('.')
        ?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringBeforeLast('.')
        ?: echoText(en = "Unknown Track", zh = "未知曲目", ja = "不明な曲")
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
            ?: 0L
        val sampleRateHz = if (Build.VERSION.SDK_INT >= 31) {
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
        } else {
            null
        }
        EchoTrack(
            id = EchoIncomingAudio.incomingTrackId(uri),
            uri = uri.toString(),
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: fallbackTitle,
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
                ?: echoText(en = "Unknown Artist", zh = "未知艺术家", ja = "不明なアーティスト"),
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() },
            durationMs = durationMs,
            mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
            sampleRateHz = sampleRateHz,
            source = LibrarySource.Unknown,
        )
    } catch (_: RuntimeException) {
        EchoTrack(
            id = EchoIncomingAudio.incomingTrackId(uri),
            uri = uri.toString(),
            title = fallbackTitle,
            artist = echoText(en = "Unknown Artist", zh = "未知艺术家", ja = "不明なアーティスト"),
            source = LibrarySource.Unknown,
        )
    } finally {
        retriever.release()
    }
}

private fun displayName(context: Context, uri: Uri): String? {
    if (uri.scheme != "content") return null
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) return@use null
                cursor.getString(index)?.takeIf { it.isNotBlank() }
            }
    }.getOrNull()
}
