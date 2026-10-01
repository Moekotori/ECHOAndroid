package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.EchoAbLoopState
import kotlinx.coroutines.*

internal object EchoAbLoopPolicy {
    fun valid(start: Long, end: Long, duration: Long): Boolean =
        duration > 0 && start >= 0 && end > start && end <= duration && end - start >= 500
    fun nextDelay(position: Long, end: Long, speed: Float): Long =
        ((end - position).coerceAtLeast(0) / speed.coerceAtLeast(0.1f)).toLong().coerceIn(15, 1000)
}

/** Owned by the media service. Only an active, playing loop schedules wakeups. */
@UnstableApi
internal class EchoAbLoopController(private val player: Player, private val scope: CoroutineScope) : Player.Listener, AutoCloseable {
    private var job: Job? = null
    private var savedRepeatMode: Int? = null
    private var seeking = false
    init { player.addListener(this) }

    fun set(trackId: String?, start: Long, end: Long): Boolean {
        if (trackId == null) { clear(); return true }
        if (trackId != player.currentMediaItem?.mediaId || player.isCurrentMediaItemLive ||
            !EchoAbLoopPolicy.valid(start, end, player.duration)) return false
        if (savedRepeatMode == null) savedRepeatMode = player.repeatMode
        EchoPlaybackProcessRuntime.publishAbLoop(EchoAbLoopState(trackId, start, end))
        player.repeatMode = Player.REPEAT_MODE_ONE
        if (player.currentPosition !in start until end) player.seekTo(start)
        refresh()
        return true
    }

    private fun clear(restoreRepeat: Boolean = true) {
        job?.cancel(); job = null
        EchoPlaybackProcessRuntime.publishAbLoop(EchoAbLoopState())
        val previous = savedRepeatMode; savedRepeatMode = null
        if (restoreRepeat && previous != null && player.repeatMode == Player.REPEAT_MODE_ONE) player.repeatMode = previous
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (seeking) return
        val loop = EchoPlaybackProcessRuntime.abLoop.value
        if (!loop.active) return
        if (player.currentMediaItem?.mediaId != loop.trackId) { clear(); return }
        if (player.repeatMode != Player.REPEAT_MODE_ONE) { clear(restoreRepeat = false); return }
        if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) && player.currentPosition < loop.startMs) player.seekTo(loop.startMs)
        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_PLAYBACK_PARAMETERS_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION)) refresh()
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        val loop = EchoPlaybackProcessRuntime.abLoop.value
        if (!seeking && reason == Player.DISCONTINUITY_REASON_SEEK && loop.active &&
            player.currentMediaItem?.mediaId == loop.trackId && newPosition.positionMs !in loop.startMs until loop.endMs) {
            clear()
        }
    }

    private fun refresh() {
        job?.cancel(); job = null
        val loop = EchoPlaybackProcessRuntime.abLoop.value
        if (!loop.active || !player.isPlaying) return
        job = scope.launch {
            while (isActive && player.isPlaying && player.currentMediaItem?.mediaId == loop.trackId) {
                if (player.currentPosition >= loop.endMs) {
                    seeking = true
                    try { player.seekTo(loop.startMs) } finally { seeking = false }
                }
                delay(EchoAbLoopPolicy.nextDelay(player.currentPosition, loop.endMs, player.playbackParameters.speed))
            }
        }
    }

    override fun close() { player.removeListener(this); clear() }
}
