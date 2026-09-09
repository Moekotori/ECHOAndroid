package app.echo.android

import app.echo.android.data.SubsonicEndpoint
import app.echo.android.data.submitSubsonicListen
import app.echo.android.data.subsonicSongIdFromTrack
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackPositionState
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal class SubsonicListenController(
    private val scope: CoroutineScope,
    private val endpointRef: AtomicReference<SubsonicEndpoint?>,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val submitListen: (
        SubsonicEndpoint,
        String,
        Boolean,
        Long?,
    ) -> Unit = { endpoint, songId, submission, timeEpochMs ->
        submitSubsonicListen(
            endpoint = endpoint,
            songId = songId,
            submission = submission,
            timeEpochMs = timeEpochMs,
        )
    },
) {
    private var active: SubsonicListen? = null
    private var collectJob: Job? = null

    fun stop() {
        collectJob?.cancel()
        collectJob = null
    }

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

    internal fun onPlayback(status: EchoPlaybackStatus, position: PlaybackPositionState) {
        val endpoint = endpointRef.get()
        val track = status.track
        val songId = track?.id?.let { subsonicSongIdFromTrack(it) }
        val now = nowEpochMs()
        if (endpoint == null || track == null || songId.isNullOrBlank()) {
            val current = active
            if (current != null && endpoint != null) {
                val accumulated = LastFmScrobbleRules.accumulatedPlayMs(
                    previouslyAccumulatedMs = current.accumulatedPlayMs,
                    wasPlaying = current.wasPlaying,
                    lastTickEpochMs = current.lastTickEpochMs,
                    nowEpochMs = now,
                )
                submitScrobbleIfDue(endpoint, current.copy(accumulatedPlayMs = accumulated))
            }
            if (LastFmScrobbleRules.shouldClearActiveScrobbleForMissingTrack(status.state)) {
                active = null
            }
            return
        }
        val current = active
        val currentPositionMs = position.positionMs.coerceAtLeast(0L)
        val durationMs = maxOf(track.durationMs, position.durationMs)
        active = if (current?.trackId != track.id) {
            if (current != null) {
                val accumulated = LastFmScrobbleRules.accumulatedPlayMs(
                    previouslyAccumulatedMs = current.accumulatedPlayMs,
                    wasPlaying = current.wasPlaying,
                    lastTickEpochMs = current.lastTickEpochMs,
                    nowEpochMs = now,
                )
                submitScrobbleIfDue(endpoint, current.copy(accumulatedPlayMs = accumulated))
            }
            SubsonicListen(
                trackId = track.id,
                songId = songId,
                durationMs = durationMs,
                startedAtEpochMs = now,
                lastTickEpochMs = if (status.isPlaying) now else 0L,
                lastPositionMs = currentPositionMs,
                wasPlaying = status.isPlaying,
            )
        } else if (
            LastFmScrobbleRules.shouldStartNewListenAfterRepeat(
                alreadyScrobbled = current.scrobbled,
                previousPositionMs = current.lastPositionMs,
                currentPositionMs = currentPositionMs,
            )
        ) {
            SubsonicListen(
                trackId = track.id,
                songId = songId,
                durationMs = durationMs,
                startedAtEpochMs = now,
                lastTickEpochMs = if (status.isPlaying) now else 0L,
                lastPositionMs = currentPositionMs,
                wasPlaying = status.isPlaying,
            )
        } else {
            current.copy(
                songId = songId,
                durationMs = maxOf(current.durationMs, durationMs),
                accumulatedPlayMs = LastFmScrobbleRules.accumulatedPlayMs(
                    previouslyAccumulatedMs = current.accumulatedPlayMs,
                    wasPlaying = current.wasPlaying,
                    lastTickEpochMs = current.lastTickEpochMs,
                    nowEpochMs = now,
                ),
                lastTickEpochMs = if (status.isPlaying) now else 0L,
                lastPositionMs = currentPositionMs,
                wasPlaying = status.isPlaying,
            )
        }

        var listen = active ?: return
        if (!listen.scrobbled &&
            LastFmScrobbleRules.shouldScrobble(listen.durationMs, listen.accumulatedPlayMs) &&
            LastFmScrobbleRules.shouldAttemptSubmit(
                alreadySubmitted = listen.scrobbled,
                lastAttemptEpochMs = listen.lastScrobbleAttemptEpochMs,
                nowEpochMs = now,
            )
        ) {
            listen = listen.copy(
                scrobbled = true,
                lastScrobbleAttemptEpochMs = now,
            )
            active = listen
            submit(
                endpoint = endpoint,
                songId = listen.songId,
                submission = true,
                timeEpochMs = listen.startedAtEpochMs,
                trackId = listen.trackId,
                revert = { currentListen -> currentListen.copy(scrobbled = false) },
            )
        }
        if (!status.isPlaying) return
        if (!listen.nowPlayingSent &&
            LastFmScrobbleRules.shouldAttemptSubmit(
                alreadySubmitted = listen.nowPlayingSent,
                lastAttemptEpochMs = listen.lastNowPlayingAttemptEpochMs,
                nowEpochMs = now,
            )
        ) {
            active = listen.copy(
                nowPlayingSent = true,
                lastNowPlayingAttemptEpochMs = now,
            )
            submit(
                endpoint = endpoint,
                songId = listen.songId,
                submission = false,
                timeEpochMs = null,
                trackId = listen.trackId,
                revert = { currentListen -> currentListen.copy(nowPlayingSent = false) },
            )
        }
    }

    private fun submitScrobbleIfDue(endpoint: SubsonicEndpoint, listen: SubsonicListen) {
        if (
            !LastFmScrobbleRules.shouldFlushScrobbleBeforeReplacing(
                alreadyScrobbled = listen.scrobbled,
                durationMs = listen.durationMs,
                listenedMs = listen.accumulatedPlayMs,
            )
        ) {
            return
        }
        submit(
            endpoint = endpoint,
            songId = listen.songId,
            submission = true,
            timeEpochMs = listen.startedAtEpochMs,
            trackId = listen.trackId,
            revert = { it },
        )
    }

    private fun submit(
        endpoint: SubsonicEndpoint,
        songId: String,
        submission: Boolean,
        timeEpochMs: Long?,
        trackId: String,
        revert: (SubsonicListen) -> SubsonicListen,
    ) {
        scope.launch(ioDispatcher) {
            try {
                submitListen(endpoint, songId, submission, timeEpochMs)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                EchoErrorLog.record(
                    EchoErrorSource.Network,
                    error.message ?: "Navidrome listen submission failed.",
                    detail = songId,
                    throwable = error,
                )
                if (!LastFmScrobbleRules.keepSubmittedFlag(false) && active?.trackId == trackId) {
                    active = active?.let(revert)
                }
            }
        }
    }
}

private data class SubsonicListen(
    val trackId: String,
    val songId: String,
    val durationMs: Long,
    val startedAtEpochMs: Long,
    val accumulatedPlayMs: Long = 0L,
    val lastTickEpochMs: Long = 0L,
    val lastPositionMs: Long = 0L,
    val wasPlaying: Boolean = false,
    val nowPlayingSent: Boolean = false,
    val scrobbled: Boolean = false,
    val lastScrobbleAttemptEpochMs: Long = 0L,
    val lastNowPlayingAttemptEpochMs: Long = 0L,
)
