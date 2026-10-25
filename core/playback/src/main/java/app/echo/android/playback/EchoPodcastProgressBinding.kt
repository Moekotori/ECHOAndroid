package app.echo.android.playback

import androidx.media3.common.Player
import app.echo.android.model.podcast.PodcastProgressSample
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Process-owned bounded event boundary; no database or network work on the player thread. */
object EchoPodcastProgressEvents {
    private val mutable = MutableSharedFlow<PodcastProgressSample>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val samples = mutable.asSharedFlow()
    internal fun publish(sample: PodcastProgressSample) { mutable.tryEmit(sample) }
}

internal class EchoPodcastProgressBinding(private val player: Player, private val scope: CoroutineScope) : Player.Listener {
    private var current: PodcastProgressSample? = null
    private var lastPublished: PodcastProgressSample? = null
    private var duration = 0L
    private var pendingReplay = false
    private var hostEnded = false
    private var job: Job? = null

    override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
        current?.let { previous ->
            val ended = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            publish(previous.copy(positionMs = if (ended && duration > 0) duration else previous.positionMs, ended = ended))
        }
        current = null
        hostEnded = false
        sample(replay = reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (oldPosition.mediaItem?.mediaId != newPosition.mediaItem?.mediaId) {
            current = current?.copy(positionMs = oldPosition.positionMs.coerceAtLeast(0))
        } else if (newPosition.positionMs < oldPosition.positionMs) {
            pendingReplay = true
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) hostEnded = true
        else if (playWhenReady && hostEnded) { pendingReplay = true; hostEnded = false }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
            sample()
            job?.cancel(); job = null
            if (player.isPlaying && current != null) job = scope.launch {
                while (isActive) { delay(15_000); sample() }
            }
        }
    }

    private fun sample(replay: Boolean = false) {
        val id = player.currentMediaItem?.mediaId?.takeIf { it.startsWith("podcast:") } ?: run {
            current?.let(::publish); current = null; return
        }
        val previous = current
        val position = player.currentPosition.coerceAtLeast(0)
        duration = player.duration.coerceAtLeast(0)
        val sample = PodcastProgressSample(id, position, player.playbackState == Player.STATE_ENDED || hostEnded,
            replay || pendingReplay || (previous?.id == id && (previous.ended || position < previous.positionMs)))
        pendingReplay = false
        current = sample.copy(replay = false)
        publish(sample)
    }

    private fun publish(sample: PodcastProgressSample) {
        if (sample != lastPublished) { lastPublished = sample; EchoPodcastProgressEvents.publish(sample) }
    }

    fun close() { sample(); job?.cancel(); player.removeListener(this) }
}
