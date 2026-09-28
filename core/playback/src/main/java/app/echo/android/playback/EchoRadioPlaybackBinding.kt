package app.echo.android.playback

import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyInfo
import app.echo.android.model.radio.EchoRadioStation

/** Drop buffered radio audio on pause, including notification/headset pause. */
@UnstableApi
internal class EchoRadioPlaybackBinding(private val player: Player) : Player.Listener {
    private var wasReadyToPlay = player.playWhenReady
    private var applyingTitle = false

    override fun onMetadata(metadata: Metadata) {
        if (applyingTitle || player.playbackState == Player.STATE_IDLE) return
        val index = player.currentMediaItemIndex
        if (index !in 0 until player.mediaItemCount) return
        val item = player.currentMediaItem ?: return
        if (!EchoRadioStation.isRadio(item.mediaId)) return
        val raw = metadata.icyTitle() ?: return
        val next = EchoRadioNowPlaying.rememberedUpdate(
            stationName = item.mediaMetadata.title?.toString().orEmpty(),
            current = item.mediaMetadata.extras?.getString(EchoRadioStreamTitleExtra),
            icyRaw = raw,
        ) ?: return
        // Same URI, so ExoPlayer updates the item in place and does not reopen the stream.
        applyingTitle = true
        try {
            player.replaceMediaItem(index, item.withRadioStreamTitle(next))
        } finally {
            applyingTitle = false
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        val paused = wasReadyToPlay && !playWhenReady
        wasReadyToPlay = playWhenReady
        val item = player.currentMediaItem ?: return
        if (paused && EchoRadioStation.isRadio(item.mediaId)) {
            player.stop()
            // Reset to the default live position. No loading until play/prepare is requested.
            player.setMediaItem(item)
        }
    }
}

private fun Metadata.icyTitle(): String? {
    for (index in 0 until length()) {
        val title = (get(index) as? IcyInfo)?.title
        if (!title.isNullOrEmpty()) return title
    }
    return null
}
