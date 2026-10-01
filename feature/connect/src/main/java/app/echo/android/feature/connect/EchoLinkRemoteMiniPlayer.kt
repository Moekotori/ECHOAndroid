package app.echo.android.feature.connect

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoArtworkSize
import app.echo.android.design.EchoIcon
import app.echo.android.design.echoClickable
import app.echo.android.model.connect.EchoRemotePlaybackSnapshot
import app.echo.android.model.connect.EchoRemotePlaybackState

/** Remote controls stay independent from the phone player's queue and progress subscriptions. */
@Composable
fun EchoLinkRemoteMiniPlayer(
    playback: EchoRemotePlaybackSnapshot,
    connected: Boolean,
    pcTitle: String,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    onPrevious: () -> Unit = {},
    remoteError: String? = null,
    onPlaybackOrderChange: (app.echo.android.model.connect.EchoRemotePlaybackOrder) -> Unit = {},
) {
    val hasMusic = playback.track != null || playback.queue.items.isNotEmpty()
    Row(modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = 64.dp)
            .remoteTrackSwipe(connected && hasMusic, onNext, onPrevious,
                trackKey = playback.queue.currentTrackId ?: playback.track?.id)
            .echoClickable(role = Role.Button, onClick = onExpand),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EchoArtworkImage(playback.track?.artworkUrl, null, Modifier.size(44.dp), sizeClass = EchoArtworkSize.Card)
            Column(Modifier.weight(1f)) {
                Text(playback.track?.title ?: stringResource(R.string.remote_choose_music),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(remoteError ?: if (connected) stringResource(R.string.remote_picker_destination, pcTitle)
                    else stringResource(R.string.feature_connect_not_connected_c4d337),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (remoteError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onPlayPause, enabled = connected && hasMusic) {
            val playing = playback.state == EchoRemotePlaybackState.Playing
            EchoIcon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                stringResource(if (playing) R.string.feature_connect_pause_pc_003bcf else R.string.feature_connect_play_on_pc_aa41d1))
        }
        IconButton(onNext, enabled = connected && hasMusic) {
            EchoIcon(Icons.Rounded.SkipNext, stringResource(R.string.feature_connect_next_on_pc_303358))
        }
        playback.playbackOrder?.let { order ->
            RemotePlaybackOrderButton(order, connected, onPlaybackOrderChange)
        }
        IconButton(onOpenQueue, enabled = connected) {
            EchoIcon(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.feature_connect_pc_queue_2e91c4))
        }
    }
}
