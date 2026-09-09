package app.echo.android

import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.playback.PlaybackPositionState
import app.echo.android.playback.EchoPlaybackProcessRuntime
import app.echo.android.playback.EchoPlaybackSurfaceSnapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal object EchoSubsonicListen {
    private val controller = SubsonicListenController(
        scope = EchoPlaybackProcessRuntime.scope,
        endpointRef = EchoSubsonicEndpointRef,
    )
    private var surfaceJob: Job? = null

    fun startFromSurface() {
        controller.stop()
        surfaceJob?.cancel()
        surfaceJob = EchoPlaybackProcessRuntime.scope.launch {
            EchoPlaybackProcessRuntime.surface.collect { snapshot ->
                val (status, position) = snapshot.toListenPlayback()
                controller.onPlayback(status, position)
            }
        }
    }

    fun startFromPlayback(
        playbackStatus: StateFlow<EchoPlaybackStatus>,
        playbackPosition: StateFlow<PlaybackPositionState>,
        settingsReady: Flow<*>,
    ) {
        surfaceJob?.cancel()
        surfaceJob = null
        controller.start(
            playbackStatus = playbackStatus,
            playbackPosition = playbackPosition,
            settingsReady = settingsReady,
        )
    }
}

private fun EchoPlaybackSurfaceSnapshot.toListenPlayback(): Pair<EchoPlaybackStatus, PlaybackPositionState> {
    val track = mediaId
        ?.takeIf { hasTrack && it.isNotBlank() }
        ?.let { id ->
            EchoTrackRef(
                id = id,
                uri = playUri.orEmpty(),
                title = title,
                artist = artist,
                durationMs = durationMs,
            )
        }
    val status = EchoPlaybackStatus(
        state = when {
            isPlaying -> EchoPlaybackState.Playing
            hasTrack -> EchoPlaybackState.Paused
            else -> EchoPlaybackState.Idle
        },
        track = track,
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaying = isPlaying,
    )
    val position = PlaybackPositionState(
        positionMs = positionMs,
        durationMs = durationMs,
    )
    return status to position
}
