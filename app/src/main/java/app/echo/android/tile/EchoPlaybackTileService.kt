package app.echo.android.tile

import android.content.ComponentName
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.echo.android.R
import app.echo.android.playback.EchoPlaybackProcessRuntime
import app.echo.android.playback.EchoPlaybackService
import app.echo.android.playback.PlaybackSessionPolicy
import app.echo.android.widget.EchoPlaybackRemote

@UnstableApi
class EchoPlaybackTileService : TileService() {
    private var controller: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateTileFromPlayer(player)
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileFromSnapshot()
        val token = SessionToken(this, ComponentName(this, EchoPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                if (controllerFuture !== future) return@addListener
                val connected = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = connected
                connected.addListener(playerListener)
                updateTileFromPlayer(connected)
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    override fun onStopListening() {
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        controllerFuture = null
        super.onStopListening()
    }

    override fun onClick() {
        val connected = controller
        if (connected != null) {
            if (connected.playWhenReady) {
                connected.pause()
            } else {
                if (
                    PlaybackSessionPolicy.shouldPrepareBeforePlay(
                        hasPlayerError = connected.playerError != null,
                        playbackStateIdle = connected.playbackState == Player.STATE_IDLE,
                    )
                ) {
                    connected.prepare()
                }
                connected.play()
            }
            updateTileFromPlayer(connected)
            return
        }
        EchoPlaybackRemote.togglePlayPause(this)
        updateTileFromSnapshot()
    }

    private fun updateTileFromSnapshot() {
        val snapshot = EchoPlaybackProcessRuntime.surfaceSnapshot
        applyTile(
            isPlaying = snapshot.isPlaying,
            hasTrack = snapshot.hasTrack,
            title = snapshot.title,
            artist = snapshot.artist,
        )
    }

    private fun updateTileFromPlayer(player: Player) {
        val metadata = player.mediaMetadata
        applyTile(
            isPlaying = player.isPlaying,
            hasTrack = player.currentMediaItem != null,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
        )
    }

    private fun applyTile(
        isPlaying: Boolean,
        hasTrack: Boolean,
        title: String,
        artist: String,
    ) {
        val tile = qsTile ?: return
        tile.state = if (isPlaying) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = when {
            title.isNotBlank() -> title
            hasTrack -> getString(R.string.app_name)
            else -> getString(R.string.qs_tile_play_pause)
        }
        if (Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when {
                isPlaying && artist.isNotBlank() -> artist
                isPlaying -> getString(R.string.qs_tile_playing)
                hasTrack -> getString(R.string.qs_tile_paused)
                else -> getString(R.string.qs_tile_idle)
            }
        }
        tile.updateTile()
    }
}
