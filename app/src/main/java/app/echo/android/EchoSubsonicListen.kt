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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

internal object EchoSubsonicListen {
    private var outbox: app.echo.android.data.SubsonicScrobbleOutbox? = null
    private var recoveryJob: Job? = null
    fun configure(context: android.content.Context) { outbox = navidromeOutbox(context) }
    fun onCredentialsReady() {
        recoveryJob?.cancel()
        val endpoint = EchoSubsonicEndpointRef.get() ?: return
        val store = outbox ?: return
        recoveryJob = EchoPlaybackProcessRuntime.scope.launch(Dispatchers.IO) {
            val owner = currentCoroutineContext()
            store.flushPending(endpoint) { owner.isActive && EchoSubsonicEndpointRef.get()?.sourceId == endpoint.sourceId }
        }
    }
    private val controller = SubsonicListenController(
        scope = EchoPlaybackProcessRuntime.scope,
        endpointRef = EchoSubsonicEndpointRef,
        submitListen = { endpoint, songId, submission, timestamp ->
            val store = outbox
            if (submission && store != null && timestamp != null) store.submit(endpoint, songId, timestamp)
            else app.echo.android.data.submitSubsonicListen(endpoint, songId, submission, timestamp)
        },
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
