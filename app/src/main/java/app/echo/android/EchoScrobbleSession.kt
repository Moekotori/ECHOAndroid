package app.echo.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import app.echo.android.data.ScrobbleOutboxStore
import java.io.File
import kotlinx.coroutines.Job
import app.echo.android.data.EchoAppSettings
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.playback.PlaybackPositionState
import app.echo.android.playback.EchoPlaybackSurfaceSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Application-owned scrobblers keep the same listen when the activity is recreated. */
internal class EchoScrobbleSession(
    context: Context,
    scope: CoroutineScope,
    private val surface: StateFlow<EchoPlaybackSurfaceSnapshot>,
) {
    val lastFm = LastFmScrobbleController(scope, ScrobbleOutboxStore(File(context.noBackupFilesDir, "lastfm-listens.json")))
    val listenBrainz = ListenBrainzScrobbleController(scope, ScrobbleOutboxStore(File(context.noBackupFilesDir, "listenbrainz-listens.json")))
    init {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                lastFm.retryPendingWhenConnected()
                listenBrainz.retryPendingWhenConnected()
            }
        }
        connectivity?.registerDefaultNetworkCallback(callback)
        scope.coroutineContext[Job]?.invokeOnCompletion {
            connectivity?.unregisterNetworkCallback(callback)
        }
    }

    private var started = false

    fun start(settings: Flow<EchoAppSettings>) {
        if (started) return
        started = true
        val playback = surface.distinctUntilChanged { previous, next ->
            previous.mediaId == next.mediaId && previous.hasTrack == next.hasTrack &&
                previous.isPlaying == next.isPlaying &&
                previous.positionMs / 1_000L == next.positionMs / 1_000L &&
                previous.durationMs == next.durationMs && previous.title == next.title &&
                previous.artist == next.artist && previous.album == next.album
        }.map { it.toScrobblePlayback() }
        lastFm.start(settings, playback)
        listenBrainz.start(settings, playback)
    }
}

internal data class EchoScrobblePlayback(
    val status: EchoPlaybackStatus,
    val position: PlaybackPositionState,
)

internal fun EchoPlaybackSurfaceSnapshot.toScrobblePlayback(): EchoScrobblePlayback {
    val track = mediaId?.takeIf { hasTrack && it.isNotBlank() }?.let { id ->
        EchoTrackRef(
            id = id,
            uri = playUri.orEmpty(),
            title = title,
            artist = artist,
            album = album,
            artworkUri = artworkUri,
            durationMs = durationMs,
        )
    }
    return EchoScrobblePlayback(
        status = EchoPlaybackStatus(
            state = when {
                isPlaying -> EchoPlaybackState.Playing
                hasTrack -> EchoPlaybackState.Paused
                else -> EchoPlaybackState.Idle
            },
            track = track,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
        ),
        position = PlaybackPositionState(positionMs = positionMs, durationMs = durationMs),
    )
}
