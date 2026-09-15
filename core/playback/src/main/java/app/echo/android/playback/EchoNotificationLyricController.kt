package app.echo.android.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.os.SystemClock

/** Service-owned: current lyric line from the playback clock, not a UI ticker. */
@UnstableApi
internal class EchoNotificationLyricController(
    private val player: Player,
    private val scope: CoroutineScope,
    private val onLine: (String?) -> Unit,
) : Player.Listener, AutoCloseable {
    private var document: EchoNotificationLyricDocument? = null
    private var job: Job? = null
    private var lastText: String? = null
    private var lastPublishElapsedRealtime = 0L

    init {
        player.addListener(this)
    }

    fun setDocument(document: EchoNotificationLyricDocument?) {
        val trackChanged = this.document?.trackId != document?.trackId
        this.document = document
        if (trackChanged) {
            lastPublishElapsedRealtime = 0L
            publish(null)
        }
        refresh()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                Player.EVENT_TIMELINE_CHANGED,
            )
        ) {
            refresh()
        }
    }

    private fun refresh() {
        job?.cancel()
        job = null
        val mediaId = player.currentMediaItem?.mediaId
        val lines = document?.takeIf { it.trackId == mediaId }?.lines.orEmpty()
        if (mediaId.isNullOrBlank() || lines.isEmpty()) {
            publish(null)
            return
        }
        job = scope.launch {
            while (isActive) {
                val position = player.currentPosition.coerceAtLeast(0L)
                val text = EchoNotificationLyricPolicy.primaryText(lines, position)
                    ?.let(EchoNotificationLyricPolicy::clampText)
                val elapsed = SystemClock.elapsedRealtime() - lastPublishElapsedRealtime
                if (EchoNotificationLyricPolicy.shouldPublish(lastText, text, elapsed)) {
                    publish(text)
                } else if (lastText != text) {
                    val wait = (EchoNotificationLyricPolicy.MinUpdateIntervalMs - elapsed)
                        .coerceAtLeast(EchoNotificationLyricPolicy.MinScheduleDelayMs)
                    delay(wait)
                    if (!isActive) return@launch
                    val later = EchoNotificationLyricPolicy.primaryText(lines, player.currentPosition.coerceAtLeast(0L))
                        ?.let(EchoNotificationLyricPolicy::clampText)
                    publish(later)
                }
                if (!player.isPlaying) break
                val nextStart = EchoNotificationLyricPolicy.nextStartMs(lines, player.currentPosition.coerceAtLeast(0L))
                    ?: break
                delay(
                    EchoNotificationLyricPolicy.delayMs(
                        player.currentPosition.coerceAtLeast(0L),
                        nextStart,
                        player.playbackParameters.speed,
                    ),
                )
            }
        }
    }

    private fun publish(text: String?) {
        if (lastText == text) return
        lastText = text
        lastPublishElapsedRealtime = SystemClock.elapsedRealtime()
        onLine(text)
    }

    override fun close() {
        job?.cancel()
        job = null
        player.removeListener(this)
        if (lastText != null) {
            lastText = null
            onLine(null)
        }
    }
}
