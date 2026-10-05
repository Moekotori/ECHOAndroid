package app.echo.android.playback

import android.provider.MediaStore
import androidx.media3.common.MediaItem
import app.echo.android.model.playback.EchoMediaSearchRequest
import app.echo.android.model.playback.EchoMediaSearchKind

data class EchoPlaybackSearchResult(val items: List<EchoPlaybackBrowseItem>, val startIndex: Int = 0)

internal fun MediaItem.voiceSearchRequest(): EchoMediaSearchRequest? {
    val query = requestMetadata.searchQuery ?: return null
    val extras = requestMetadata.extras
    fun hint(key: String) = extras?.getString(key)?.trim()?.take(256)?.takeIf { it.isNotEmpty() }
    return EchoMediaSearchRequest(query.take(256),
        hint(MediaStore.EXTRA_MEDIA_FOCUS), hint(MediaStore.EXTRA_MEDIA_TITLE),
        hint(MediaStore.EXTRA_MEDIA_ALBUM), hint(MediaStore.EXTRA_MEDIA_ARTIST),
        hint(MediaStore.EXTRA_MEDIA_PLAYLIST), hint(MediaStore.EXTRA_MEDIA_GENRE))
}

/** Stable ranking never lets an artist/album-only match win a requested song title. */
object EchoVoiceSearchPolicy {
    fun select(request: EchoMediaSearchRequest, candidates: List<EchoPlaybackBrowseItem>): EchoPlaybackBrowseItem? {
        val kind = when (request.kind) {
            EchoMediaSearchKind.Playlist -> EchoPlaybackBrowseKind.Playlist
            EchoMediaSearchKind.Song -> EchoPlaybackBrowseKind.Track
            EchoMediaSearchKind.Album -> EchoPlaybackBrowseKind.Album
            EchoMediaSearchKind.Artist -> EchoPlaybackBrowseKind.Artist
            EchoMediaSearchKind.Genre -> EchoPlaybackBrowseKind.Genre
            EchoMediaSearchKind.Any -> null
        }
        val term = request.term
        return candidates.asSequence().filter { it.playable && (kind == null || it.kind == kind) }
            .filter { it.title.contains(term, ignoreCase = true) }
            .filter { request.artist == null || (it.kind != EchoPlaybackBrowseKind.Track && it.kind != EchoPlaybackBrowseKind.Album) ||
                it.subtitle.orEmpty().contains(request.artist.orEmpty(), ignoreCase = true) }
            .sortedWith(compareBy<EchoPlaybackBrowseItem> {
                when {
                    it.title.equals(term, ignoreCase = true) -> 0
                    it.title.startsWith(term, ignoreCase = true) -> 1
                    else -> 2
                }
            }.thenBy { it.mediaId })
            .firstOrNull()
    }
}
