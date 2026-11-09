package app.echo.android.feature.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemotePlaybackOrder
import app.echo.android.model.connect.EchoRemoteStatus

@Composable
fun echoLinkPlayerNotice(status: EchoRemoteStatus): String =
    status.error?.takeIf(String::isNotBlank) ?: when {
        status.connectionState != EchoRemoteConnectionState.Connected ->
            remoteConnectionLabel(status.connectionState)
        status.controlPending -> stringResource(R.string.remote_control_pending)
        else -> stringResource(R.string.feature_connect_playing_on_pc_580a8a)
    }

/** PC-specific controls for the shared Now Playing menu. */
@Composable
fun EchoLinkPlayerControls(
    status: EchoRemoteStatus,
    onStop: () -> Unit,
    onVolume: (Float) -> Unit,
    onPlaybackOrderChange: (EchoRemotePlaybackOrder) -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val playback = status.playback
    val connected = status.connectionState == EchoRemoteConnectionState.Connected
    val controlsEnabled = connected || status.connectionState == EchoRemoteConnectionState.Reconnecting
    RemotePlaybackOrderControls(playback.playbackOrder, connected, onPlaybackOrderChange)
    key(status.connectionId, playback.volumeControlEnabled) {
        RemoteVolumeControl(playback.volume, controlsEnabled && playback.volumeControlEnabled, onVolume)
    }
    if (!playback.volumeControlEnabled) {
        Text(
            stringResource(if (playback.volumeLockedReason == "fixed_volume") {
                R.string.remote_fixed_volume
            } else R.string.remote_volume_locked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            playback.outputMode,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextButton(onOpenLibrary, enabled = connected) {
            Text(stringResource(R.string.remote_choose_music))
        }
        TextButton(onStop, enabled = controlsEnabled && playback.track != null) {
            Text(stringResource(R.string.remote_stop))
        }
    }
}
