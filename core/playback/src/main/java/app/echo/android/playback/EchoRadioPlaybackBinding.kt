package app.echo.android.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.radio.EchoRadioStation

/** Drop buffered radio audio on pause, including notification/headset pause. */
@UnstableApi
internal class EchoRadioPlaybackBinding(private val player: Player) : Player.Listener {
    private var wasReadyToPlay = player.playWhenReady

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
