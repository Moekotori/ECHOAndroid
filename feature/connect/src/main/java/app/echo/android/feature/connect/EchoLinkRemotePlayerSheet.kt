package app.echo.android.feature.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.echo.android.model.connect.EchoRemoteLibraryState
import app.echo.android.model.connect.EchoRemotePlaybackSnapshot
import app.echo.android.model.connect.EchoRemotePlaybackState

/** Opens above the current page, including album and artist details. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoLinkRemotePlayerSheet(
    playback: EchoRemotePlaybackSnapshot,
    connected: Boolean,
    remoteError: String?,
    active: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Float) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenQueue: () -> Unit,
    onDismiss: () -> Unit,
    onPlaybackOrderChange: (app.echo.android.model.connect.EchoRemotePlaybackOrder) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!remoteError.isNullOrBlank()) {
                Text(remoteError, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            RemoteNowPlaying(
                title = playback.track?.title.orEmpty(),
                artist = playback.track?.artist.orEmpty(),
                artworkUrl = playback.track?.artworkUrl,
                isPlaying = playback.state == EchoRemotePlaybackState.Playing,
                controlsEnabled = connected,
                positionMs = playback.positionMs,
                durationMs = playback.durationMs,
                volume = playback.volume,
                volumeControlEnabled = playback.volumeControlEnabled,
                volumeLockedReason = playback.volumeLockedReason,
                onPlayPause = onPlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onStop = onStop,
                onSeek = onSeek,
                onVolume = onVolume,
                onOpenLibrary = onOpenLibrary,
                onOpenQueue = onOpenQueue,
                outputMode = playback.outputMode,
                currentTrackId = playback.queue.currentTrackId ?: playback.track?.id,
                queueCount = playback.queue.totalCount,
                active = active,
            )
            RemotePlaybackOrderControls(playback.playbackOrder, connected, onPlaybackOrderChange)
        }
    }
}
