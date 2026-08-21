package app.echo.android.playback

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.EchoTrackRef

data class EchoPlaybackSessionSnapshot(
    val queue: List<EchoTrackRef>,
    val currentIndex: Int,
    val positionMs: Long,
    val playWhenReady: Boolean,
    val shuffleEnabled: Boolean = false,
    val repeatMode: EchoRepeatMode = EchoRepeatMode.Off,
    val playbackSpeed: Float = 1f,
    val playbackPitch: Float = 1f,
)

interface EchoPlaybackSessionStore {
    suspend fun load(): EchoPlaybackSessionSnapshot?

    suspend fun save(snapshot: EchoPlaybackSessionSnapshot?)

    companion object {
        val Empty: EchoPlaybackSessionStore = object : EchoPlaybackSessionStore {
            override suspend fun load(): EchoPlaybackSessionSnapshot? = null

            override suspend fun save(snapshot: EchoPlaybackSessionSnapshot?) = Unit
        }
    }
}

@UnstableApi
fun Player.toPlaybackSessionSnapshot(): EchoPlaybackSessionSnapshot? {
    if (mediaItemCount <= 0) return null
    val currentIndex = currentMediaItemIndex.takeIf { it in 0 until mediaItemCount } ?: return null
    val currentDurationMs = duration.takeIf { it > 0L } ?: 0L
    val queue = (0 until mediaItemCount).map { index ->
        getMediaItemAt(index).toEchoTrackRef(
            durationMs = if (index == currentIndex) currentDurationMs else 0L,
        )
    }
    return EchoPlaybackSessionSnapshot(
        queue = queue,
        currentIndex = currentIndex,
        positionMs = currentPosition.coerceAtLeast(0L),
        playWhenReady = playWhenReady,
        shuffleEnabled = shuffleModeEnabled,
        repeatMode = repeatMode.toEchoRepeatMode(),
        playbackSpeed = playbackParameters.speed,
        playbackPitch = playbackParameters.pitch,
    )
}

@UnstableApi
fun Player.applyPlaybackSessionSnapshot(
    snapshot: EchoPlaybackSessionSnapshot,
    play: Boolean,
) {
    setMediaItems(
        snapshot.queue.map { it.toMediaItem() },
        snapshot.currentIndex,
        snapshot.positionMs.coerceAtLeast(0L),
    )
    shuffleModeEnabled = snapshot.shuffleEnabled
    repeatMode = snapshot.repeatMode.toPlayerRepeatMode()
    playbackParameters = PlaybackParameters(snapshot.playbackSpeed, snapshot.playbackPitch)
    prepare()
    if (play) play() else pause()
}
