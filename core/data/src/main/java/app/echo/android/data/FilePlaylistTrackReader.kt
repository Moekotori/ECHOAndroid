package app.echo.android.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import app.echo.android.model.library.LibrarySource
import kotlinx.coroutines.CancellationException

internal fun readFilePlaylistTrack(context: Context, playlistId: String, uri: Uri): LibraryTrackEntity? {
    val resolver = context.contentResolver
    var name = uri.lastPathSegment.orEmpty(); var size = 0L
    try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) { name = it.getString(0).orEmpty(); size = if (it.isNull(1)) 0L else it.getLong(1) }
        }
        val mime = resolver.getType(uri)
        if (!isSupportedIncomingAudioFile(name, mime)) return null
        val input = resolver.openInputStream(uri) ?: return null
        val tags = input.use { stream ->
            try { readLocalAudioTags(stream) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: java.io.IOException) { null }
            catch (_: RuntimeException) { null }
        }
        val reader = MediaMetadataRetriever()
        try {
            try { reader.setDataSource(context, uri) } catch (_: RuntimeException) { /* tags and filename remain usable */ }
            fun metadata(key: Int) = try { reader.extractMetadata(key) } catch (_: RuntimeException) { null }
            return LibraryTrackEntity(FilePlaylistStore.entryId(playlistId, uri.toString()), uri.toString(),
                (tags?.title?.takeIf(String::isNotBlank) ?: metadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: name.substringBeforeLast('.')).take(512),
                (tags?.artist?.takeIf(String::isNotBlank) ?: metadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "").take(512),
                tags?.album ?: metadata(MediaMetadataRetriever.METADATA_KEY_ALBUM), tags?.albumArtist ?: metadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST), null,
                metadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0) ?: 0,
                tags?.trackNumber, tags?.discNumber, tags?.year, incomingAudioMimeType(name, mime), size,
                dateModifiedSeconds = 0, source = LibrarySource.FilePlaylist.id, fileName = name.take(512), rating = tags?.rating ?: 0, embeddedRating = tags?.rating)
        } finally { reader.release() }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: java.io.IOException) { return null }
    catch (_: RuntimeException) { return null }
}
