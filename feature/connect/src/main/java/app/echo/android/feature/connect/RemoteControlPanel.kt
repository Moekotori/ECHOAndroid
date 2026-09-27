package app.echo.android.feature.connect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
    Surface(color = scheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Computer, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.feature_connect_playing_on_pc_580a8a),
                    style = MaterialTheme.typography.labelLarge, color = scheme.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                EchoArtworkImage(artworkUrl, null, Modifier.size(96.dp),
                    shape = RoundedCornerShape(16.dp), sizeClass = EchoArtworkSize.Card)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title.ifBlank { stringResource(R.string.feature_connect_no_track_selected_258d56) },
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (artist.isNotBlank() || !hasTrack) {
                        Text(if (hasTrack) artist else stringResource(R.string.remote_choose_hint),
                            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // A seek hold belongs to one track; never carry it across a PC track change.
            key(currentTrackId, title) {
                RemoteSeekControl(positionMs, durationMs, isPlaying, controlsEnabled && hasTrack, active, onSeek)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onPrevious, Modifier.size(56.dp), enabled = controlsEnabled && (hasTrack || queueCount > 0)) {
                    Icon(Icons.Rounded.SkipPrevious, stringResource(R.string.feature_connect_previous_on_pc_a0f0a7), Modifier.size(28.dp))
                }
                FilledIconButton(onPlayPause, Modifier.size(76.dp), enabled = controlsEnabled && (hasTrack || queueCount > 0), shape = CircleShape) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (isPlaying) R.string.feature_connect_pause_pc_003bcf else R.string.feature_connect_play_on_pc_aa41d1),
                        Modifier.size(38.dp))
                }
                FilledTonalIconButton(onNext, Modifier.size(56.dp), enabled = controlsEnabled && (hasTrack || queueCount > 0)) {
                    Icon(Icons.Rounded.SkipNext, stringResource(R.string.feature_connect_next_on_pc_303358), Modifier.size(28.dp))
                }
            }
            RemoteVolumeControl(volume, controlsEnabled, onVolume)
            FilledTonalButton(onOpenLibrary, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = controlsEnabled) {
                Icon(Icons.Rounded.LibraryMusic, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.remote_choose_music))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onOpenQueue, Modifier.weight(1f), enabled = controlsEnabled) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.remote_queue_count, queueCount))
                }
                TextButton(onStop, enabled = controlsEnabled && hasTrack) {
                    Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.remote_stop))
                }
            }
            if (outputMode.isNotBlank() && hasTrack) {
                Text(stringResource(R.string.feature_connect_pc_output_mode_e2a6b1, outputMode),
                    style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
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
