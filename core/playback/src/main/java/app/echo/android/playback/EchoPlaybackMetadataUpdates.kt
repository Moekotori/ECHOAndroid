package app.echo.android.playback

import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** Metadata-only replacement preserves the source, clipping, queue identity and transport state. */
@UnstableApi
internal fun updatePlayerTrackArtwork(player: Player, trackId: String, artworkUri: Uri) {
    for (index in 0 until player.mediaItemCount) {
        val item = player.getMediaItemAt(index)
        if (item.mediaId != trackId || item.mediaMetadata.artworkUri == artworkUri) continue
        val metadata = item.mediaMetadata.buildUpon().setArtworkUri(artworkUri).build()
        player.replaceMediaItem(index, item.buildUpon().setMediaMetadata(metadata).build())
    }
}
