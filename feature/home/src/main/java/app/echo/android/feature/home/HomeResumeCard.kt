package app.echo.android.feature.home

import app.echo.android.design.EchoIcon

import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkTile
import app.echo.android.design.formatDuration
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.PlaybackPositionState

@Composable
internal fun HomeResumeSection(
    status: EchoPlaybackStatus,
    positionState: State<PlaybackPositionState>?,
    onResume: () -> Unit,
) {
    val track = status.track ?: return
    val action = stringResource(if (status.isPlaying) R.string.home_resume_open_player else R.string.home_resume_play)
    Surface(onClick = onResume,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        shape = RectangleShape, color = Color.Transparent) {
        Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            ArtworkTile(track.artworkUri, modifier = Modifier.size(60.dp),
                accent = MaterialTheme.colorScheme.primary, cornerRadius = 4.dp, elevation = 0.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(if (status.isPlaying) R.string.feature_home_playing_d86b54 else R.string.home_resume),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(track.title, style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(track.artist, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ResumePosition(positionState, status.positionMs, status.durationMs)
                }
            }
            IconButton(onClick = onResume, modifier = Modifier.size(48.dp)) {
                EchoIcon(if (status.isPlaying) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Rounded.PlayArrow,
                    contentDescription = action, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ResumePosition(positionState: State<PlaybackPositionState>?, fallbackPosition: Long, fallbackDuration: Long) {
    // Only this small caption observes playback ticks.
    val position = positionState?.value
    val positionMs = position?.positionMs ?: fallbackPosition
    val durationMs = position?.durationMs?.takeIf { it > 0 } ?: fallbackDuration
    Text(stringResource(R.string.home_resume_position, formatDuration(positionMs.coerceAtLeast(0)), formatDuration(durationMs.coerceAtLeast(0))),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
