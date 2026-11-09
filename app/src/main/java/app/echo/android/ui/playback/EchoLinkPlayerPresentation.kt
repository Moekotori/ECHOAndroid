package app.echo.android.ui.playback

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import app.echo.android.connect.EchoRemoteClient
import app.echo.android.lyrics.EchoLyricsParser
import app.echo.android.lyrics.LyricsContentPolicy
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemotePlaybackOrder
import app.echo.android.model.connect.EchoRemotePlaybackSnapshot
import app.echo.android.model.connect.EchoRemotePlaybackState
import app.echo.android.model.connect.EchoRemoteStatus
import app.echo.android.model.lyrics.EchoLyricsLoadState
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.playback.PlaybackPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

internal fun EchoRemotePlaybackSnapshot.toPlayerStatus(): EchoPlaybackStatus = EchoPlaybackStatus(
    track = track?.let {
        EchoTrackRef(
            id = "link-pc:${it.id ?: it.queueId ?: it.title}",
            uri = "",
            title = it.title,
            artist = it.artist,
            album = it.album,
            artworkUri = it.artworkUrl,
            durationMs = it.durationMs,
        )
    },
    state = when (state) {
        EchoRemotePlaybackState.Idle -> EchoPlaybackState.Idle
        EchoRemotePlaybackState.Loading -> EchoPlaybackState.Loading
        EchoRemotePlaybackState.Playing -> EchoPlaybackState.Playing
        EchoRemotePlaybackState.Paused -> EchoPlaybackState.Paused
        EchoRemotePlaybackState.Stopped -> EchoPlaybackState.Stopped
        EchoRemotePlaybackState.Error -> EchoPlaybackState.Error
    },
    durationMs = durationMs,
    isPlaying = state == EchoRemotePlaybackState.Playing,
    repeatMode = if (playbackOrder == EchoRemotePlaybackOrder.RepeatOne) EchoRepeatMode.One else EchoRepeatMode.Off,
    shuffleEnabled = playbackOrder == EchoRemotePlaybackOrder.Shuffle,
)

/** Only the mini player, scrubber and lyrics subscribe to the interpolated position. */
@Composable
internal fun rememberEchoLinkPlayerPosition(
    status: EchoRemoteStatus,
    active: Boolean,
    lightweight: Boolean,
): StateFlow<PlaybackPositionState> {
    val playback = status.playback
    val duration = playback.durationMs.coerceAtLeast(0L)
    val start = playback.positionMs.coerceIn(0L, duration)
    val trackKey = playback.queue.currentTrackId ?: playback.track?.id ?: playback.track?.title
    val position = remember(status.connectionId, trackKey) {
        MutableStateFlow(PlaybackPositionState(start, duration))
    }
    val interpolate = active && duration > 0L && !status.controlPending &&
        status.connectionState == EchoRemoteConnectionState.Connected &&
        playback.state == EchoRemotePlaybackState.Playing
    LaunchedEffect(position, start, duration, playback.updatedAtEpochMs, interpolate, lightweight) {
        val anchor = SystemClock.elapsedRealtime()
        position.value = PlaybackPositionState(start, duration)
        while (interpolate && position.value.positionMs < duration) {
            delay(if (lightweight) 1_000L else 100L)
            val elapsed = SystemClock.elapsedRealtime() - anchor
            position.value = PlaybackPositionState(start + elapsed.coerceIn(0L, duration - start), duration)
        }
    }
    return position
}

@Composable
internal fun rememberEchoLinkPlayerLyrics(
    client: EchoRemoteClient,
    status: EchoRemoteStatus,
    active: Boolean,
): EchoLyricsLoadState {
    val trackId = status.playback.track?.id ?: return EchoLyricsLoadState.Idle
    val connected = status.connectionState == EchoRemoteConnectionState.Connected
    return key(status.connectionId, trackId) {
        produceState<EchoLyricsLoadState>(EchoLyricsLoadState.Loading, client, active, connected) {
            if (!active || !connected || value is EchoLyricsLoadState.Ready) return@produceState
            val remote = client.fetchLyrics(trackId)
            val lyrics = remote?.let {
                withContext(Dispatchers.Default) {
                    LyricsContentPolicy.readable(runCatching {
                        EchoLyricsParser.parse(it.rawText, it.sourceLabel)
                    }.getOrNull())
                }
            }
            value = if (lyrics == null) EchoLyricsLoadState.Missing else EchoLyricsLoadState.Ready(lyrics)
        }.value
    }
}
