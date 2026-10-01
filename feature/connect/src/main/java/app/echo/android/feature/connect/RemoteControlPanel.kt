package app.echo.android.feature.connect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.model.connect.EchoRemoteConnectionState

@Composable
internal fun RemoteNowPlaying(
    title: String,
    artist: String,
    artworkUrl: String?,
    isPlaying: Boolean,
    controlsEnabled: Boolean,
    positionMs: Long,
    durationMs: Long,
    volume: Float,
    volumeControlEnabled: Boolean,
    volumeLockedReason: String?,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (Float) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenQueue: () -> Unit,
    outputMode: String,
    currentTrackId: String?,
    queueCount: Int,
    active: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val hasTrack = currentTrackId != null || title.isNotBlank()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val coverSize = if (maxWidth < 340.dp) 112.dp else 136.dp
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                EchoArtworkImage(artworkUrl, null, Modifier.size(coverSize),
                    shape = RoundedCornerShape(12.dp), sizeClass = EchoArtworkSize.Card)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.feature_connect_playing_on_pc_580a8a),
                        style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                    Text(title.ifBlank { stringResource(R.string.feature_connect_no_track_selected_258d56) },
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (artist.isNotBlank() || !hasTrack) {
                        Text(if (hasTrack) artist else stringResource(R.string.remote_choose_hint),
                            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        // A seek hold belongs to one track; never carry it across a PC track change.
        key(currentTrackId, title) {
            RemoteSeekControl(positionMs, durationMs, isPlaying, controlsEnabled && hasTrack, active, onSeek)
        }
        RemoteTransportControls(isPlaying, controlsEnabled && (hasTrack || queueCount > 0),
            onPrevious, onPlayPause, onNext)
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            key(volumeControlEnabled) {
                RemoteVolumeControl(volume, controlsEnabled && volumeControlEnabled, onVolume)
            }
            if (!volumeControlEnabled) {
                Text(
                    stringResource(if (volumeLockedReason == "fixed_volume") {
                        R.string.remote_fixed_volume
                    } else {
                        R.string.remote_volume_locked
                    }),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onOpenLibrary, Modifier.weight(1f).heightIn(min = 52.dp), enabled = controlsEnabled,
                shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.remote_choose_music), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(onOpenQueue, Modifier.weight(1f).heightIn(min = 52.dp), enabled = controlsEnabled,
                shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.remote_queue_count, queueCount), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (hasTrack) outputMode else "", modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onStop, enabled = controlsEnabled && hasTrack) {
                Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.remote_stop))
            }
        }
    }
}

@Composable
internal fun remoteConnectionLabel(state: EchoRemoteConnectionState): String = stringResource(when (state) {
    EchoRemoteConnectionState.Disconnected -> R.string.feature_connect_not_connected_c4d337
    EchoRemoteConnectionState.Pairing -> R.string.feature_connect_pairing_1a1d00
    EchoRemoteConnectionState.Connecting -> R.string.feature_connect_connecting_5a83dc
    EchoRemoteConnectionState.Connected -> R.string.feature_connect_connected_6b85ee
    EchoRemoteConnectionState.Reconnecting -> R.string.feature_connect_reconnecting_6c545f
    EchoRemoteConnectionState.Error -> R.string.feature_connect_connection_failed_321409
})
