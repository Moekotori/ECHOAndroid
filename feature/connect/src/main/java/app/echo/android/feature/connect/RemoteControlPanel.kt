package app.echo.android.feature.connect

import app.echo.android.feature.connect.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.model.connect.EchoRemoteConnectionState

@Composable
internal fun RemoteNowPlaying(
    title: String, artist: String, artworkUrl: String?, isPlaying: Boolean, controlsEnabled: Boolean,
    onPlayPause: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
) {
    ConnectSection(stringResource(L10nR.string.feature_connect_playing_on_pc_580a8a)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EchoArtworkImage(artworkUri = artworkUrl, contentDescription = null, modifier = Modifier.size(72.dp),
                shape = ConnectControlShape, sizeClass = EchoArtworkSize.Thumbnail)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title.ifBlank { stringResource(L10nR.string.feature_connect_no_track_selected_258d56) },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                if (artist.isNotBlank()) ConnectNote(artist)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, enabled = controlsEnabled, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipPrevious, stringResource(L10nR.string.feature_connect_previous_on_pc_a0f0a7))
            }
            Button(onClick = onPlayPause, enabled = controlsEnabled, shape = ConnectControlShape, modifier = Modifier.padding(horizontal = 16.dp).heightIn(min = 48.dp)) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isPlaying) stringResource(L10nR.string.feature_connect_pause_pc_003bcf) else stringResource(L10nR.string.feature_connect_play_on_pc_aa41d1))
            }
            IconButton(onClick = onNext, enabled = controlsEnabled, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipNext, stringResource(L10nR.string.feature_connect_next_on_pc_303358))
            }
        }
    }
}

@Composable
internal fun remoteConnectionLabel(state: EchoRemoteConnectionState): String = when (state) {
    EchoRemoteConnectionState.Disconnected -> stringResource(L10nR.string.feature_connect_not_connected_c4d337)
    EchoRemoteConnectionState.Pairing -> stringResource(L10nR.string.feature_connect_pairing_1a1d00)
    EchoRemoteConnectionState.Connecting -> stringResource(L10nR.string.feature_connect_connecting_5a83dc)
    EchoRemoteConnectionState.Connected -> stringResource(L10nR.string.feature_connect_connected_6b85ee)
    EchoRemoteConnectionState.Reconnecting -> stringResource(L10nR.string.feature_connect_reconnecting_6c545f)
    EchoRemoteConnectionState.Error -> stringResource(L10nR.string.feature_connect_connection_failed_321409)
}
