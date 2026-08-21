package app.echo.android.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.EchoLinkPlaybackUri

enum class EchoPlaybackBrowseKind {
    Root,
    Albums,
    Artists,
    Playlists,
    Favorites,
    Tracks,
    Album,
    Artist,
    Playlist,
    Track,
}

data class EchoPlaybackBrowseItem(
    val mediaId: String,
    val title: String,
    val subtitle: String? = null,
    val artworkUri: String? = null,
    val playUri: String? = null,
    val persistUri: String? = null,
    val browsable: Boolean,
    val playable: Boolean,
    val durationMs: Long = 0L,
    val kind: EchoPlaybackBrowseKind = EchoPlaybackBrowseKind.Track,
)

interface EchoPlaybackCatalog {
    suspend fun root(): EchoPlaybackBrowseItem

    suspend fun children(parentId: String, page: Int, pageSize: Int): List<EchoPlaybackBrowseItem>

    suspend fun item(mediaId: String): EchoPlaybackBrowseItem?

    suspend fun search(query: String, page: Int, pageSize: Int): List<EchoPlaybackBrowseItem>

    suspend fun playableQueue(mediaId: String): List<EchoPlaybackBrowseItem>

    suspend fun isFavorite(trackId: String): Boolean

    suspend fun toggleFavorite(trackId: String): Boolean?

    companion object {
        val Empty: EchoPlaybackCatalog = object : EchoPlaybackCatalog {
            override suspend fun root(): EchoPlaybackBrowseItem =
                EchoPlaybackBrowseItem(
                    mediaId = EchoPlaybackLibraryIds.ROOT,
                    title = "ECHO",
                    browsable = true,
                    playable = false,
                    kind = EchoPlaybackBrowseKind.Root,
                )

            override suspend fun children(
                parentId: String,
                page: Int,
                pageSize: Int,
            ): List<EchoPlaybackBrowseItem> = emptyList()

            override suspend fun item(mediaId: String): EchoPlaybackBrowseItem? =
                root().takeIf { mediaId == EchoPlaybackLibraryIds.ROOT }

            override suspend fun search(
                query: String,
                page: Int,
                pageSize: Int,
            ): List<EchoPlaybackBrowseItem> = emptyList()

            override suspend fun playableQueue(mediaId: String): List<EchoPlaybackBrowseItem> =
                emptyList()

            override suspend fun isFavorite(trackId: String): Boolean = false

            override suspend fun toggleFavorite(trackId: String): Boolean? = null
        }
    }
}

@UnstableApi
fun EchoPlaybackBrowseItem.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setArtist(subtitle)
        .setIsBrowsable(browsable)
        .setIsPlayable(playable)
        .setMediaType(kind.toMediaType())
        .setArtworkUri(artworkUri?.takeIf { it.isNotBlank() }?.let(Uri::parse))
        .also { builder ->
            if (durationMs > 0L) builder.setDurationMs(durationMs)
            if (kind == EchoPlaybackBrowseKind.Album) builder.setAlbumTitle(title)
        }
        .setExtras(
            playUri?.let { uri ->
                playbackItemExtras(
                    playUri = uri,
                    persistUri = persistUri ?: EchoLinkPlaybackUri.persistableUri(mediaId, uri),
                    artworkUri = artworkUri,
                )
            },
        )
        .build()
    val builder = MediaItem.Builder()
        .setMediaId(mediaId)
        .setMediaMetadata(metadata)
    if (playable) {
        playUri?.takeIf { it.isNotBlank() }?.let { builder.setUri(it) }
    }
    return builder.build()
}

@UnstableApi
private fun EchoPlaybackBrowseKind.toMediaType(): Int = when (this) {
    EchoPlaybackBrowseKind.Root,
    EchoPlaybackBrowseKind.Favorites,
    EchoPlaybackBrowseKind.Tracks,
    -> MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
    EchoPlaybackBrowseKind.Albums -> MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
    EchoPlaybackBrowseKind.Artists -> MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
    EchoPlaybackBrowseKind.Playlists -> MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
    EchoPlaybackBrowseKind.Album -> MediaMetadata.MEDIA_TYPE_ALBUM
    EchoPlaybackBrowseKind.Artist -> MediaMetadata.MEDIA_TYPE_ARTIST
    EchoPlaybackBrowseKind.Playlist -> MediaMetadata.MEDIA_TYPE_PLAYLIST
    EchoPlaybackBrowseKind.Track -> MediaMetadata.MEDIA_TYPE_MUSIC
}
