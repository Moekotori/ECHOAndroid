package app.echo.android

import app.echo.android.data.SubsonicEndpoint
import app.echo.android.data.submitSubsonicListen
import app.echo.android.data.subsonicSongIdFromTrack
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackPositionState
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

internal class SubsonicListenController(
    private val scope: CoroutineScope,
    private val endpointRef: AtomicReference<SubsonicEndpoint?>,
) {
    private var active: SubsonicListen? = null
    private var collectJob: Job? = null

    fun start(
        playbackStatus: StateFlow<EchoPlaybackStatus>,
        playbackPosition: StateFlow<PlaybackPositionState>,
        settingsReady: Flow<*>,
    ) {
        collectJob?.cancel()
        collectJob = scope.launch {
            combine(settingsReady, playbackStatus, playbackPosition) { _, status, position ->
                status to position
            }.collect { (status, position) ->
                onPlayback(status, position)
            }
        }
    }

    private fun onPlayback(status: EchoPlaybackStatus, position: PlaybackPositionState) {
        val endpoint = endpointRef.get()
        val track = status.track
        val songId = track?.id?.let { subsonicSongIdFromTrack(it) }
        if (endpoint == null || songId.isNullOrBlank() || track == null) {
            if (LastFmScrobbleRules.shouldClearActiveScrobbleForMissingTrack(status.state)) {
                active = null
            }
            return
        }
        val nowEpochMs = System.currentTimeMillis()
        val current = active
        val durationMs = maxOf(track.durationMs, position.durationMs)
        active = if (current?.trackId != track.id) {
            SubsonicListen(
                trackId = track.id,
                durationMs = durationMs,
                startedAtEpochSeconds = nowEpochMs / 1000L,
                lastTickEpochMs = if (status.isPlaying) nowEpochMs else 0L,
                wasPlaying = status.isPlaying,
            )
        } else {
            current.copy(
                durationMs = maxOf(current.durationMs, durationMs),
                accumulatedPlayMs = LastFmScrobbleRules.accumulatedPlayMs(
                    previouslyAccumulatedMs = current.accumulatedPlayMs,
                    wasPlaying = current.wasPlaying,
                    lastTickEpochMs = current.lastTickEpochMs,
                    nowEpochMs = nowEpochMs,
                ),
                lastTickEpochMs = if (status.isPlaying) nowEpochMs else 0L,
                wasPlaying = status.isPlaying,
            )
        }
        val listen = active ?: return
        if (!listen.scrobbled &&
            LastFmScrobbleRules.shouldScrobble(listen.durationMs, listen.accumulatedPlayMs)
        ) {
            active = listen.copy(scrobbled = true)
            submit(endpoint, songId, submission = true, timeSeconds = listen.startedAtEpochSeconds)
        }
        if (!status.isPlaying) return
        if (!listen.nowPlayingSent) {
            active = (active ?: listen).copy(nowPlayingSent = true)
            submit(endpoint, songId, submission = false, timeSeconds = null)
        }
    }

    private fun submit(
        endpoint: SubsonicEndpoint,
        songId: String,
        submission: Boolean,
        timeSeconds: Long?,
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                submitSubsonicListen(
                    endpoint = endpoint,
                    songId = songId,
                    submission = submission,
                    timeSeconds = timeSeconds,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
            }
        }
    }
}

private data class SubsonicListen(
    val trackId: String,
    val durationMs: Long,
    val startedAtEpochSeconds: Long,
    val accumulatedPlayMs: Long = 0L,
    val lastTickEpochMs: Long = 0L,
    val wasPlaying: Boolean = false,
    val nowPlayingSent: Boolean = false,
    val scrobbled: Boolean = false,
)
