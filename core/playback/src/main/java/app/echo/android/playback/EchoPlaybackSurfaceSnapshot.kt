package app.echo.android.playback

import androidx.media3.common.Player

data class EchoPlaybackSurfaceSnapshot(
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val hasTrack: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val mediaId: String? = null,
    val artworkUri: String? = null,
    val playUri: String? = null,
)

fun Player.toPlaybackSurfaceSnapshot(): EchoPlaybackSurfaceSnapshot {
    val item = currentMediaItem
    val metadata = item?.mediaMetadata
    val playUri = item?.localConfiguration?.uri?.toString()
        ?: metadata?.extras?.getString(EchoEmbeddedArtworkSourceUriExtra)
    return EchoPlaybackSurfaceSnapshot(
        title = metadata?.title?.toString().orEmpty(),
        artist = metadata?.artist?.toString().orEmpty(),
        isPlaying = isPlaying,
        hasTrack = item != null,
        repeatMode = repeatMode,
        mediaId = item?.mediaId,
        artworkUri = metadata?.artworkUri?.toString(),
        playUri = playUri,
    )
}
