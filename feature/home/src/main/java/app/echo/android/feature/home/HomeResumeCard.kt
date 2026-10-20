package app.echo.android.feature.home

import app.echo.android.design.displayArtist
import app.echo.android.design.displayTrack
import app.echo.android.design.EchoIcon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    val scheme = MaterialTheme.colorScheme
    val action = stringResource(if (status.isPlaying) R.string.home_resume_open_player else R.string.home_resume_play)
    Box(Modifier.padding(horizontal = 24.dp)) {
        HomeBlock(onClick = onResume, verticalPadding = 4.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ArtworkTile(track.artworkUri, modifier = Modifier.size(64.dp),
                        accent = scheme.primary, cornerRadius = HomeCoverCorner, elevation = 0.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(if (status.isPlaying) R.string.feature_home_playing_d86b54 else R.string.home_resume),
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                            color = scheme.primary)
                        Text(displayTrack(track.title), style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold, color = scheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(displayArtist(track.artist), style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // The whole row is the touch target; the icon only signals the action.
                    EchoIcon(if (status.isPlaying) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Rounded.PlayArrow,
                        contentDescription = action, tint = scheme.primary, modifier = Modifier.size(28.dp))
                }
                ResumeProgress(positionState, status.positionMs, status.durationMs)
            }
        }
    }
}

@Composable
private fun ResumeProgress(positionState: State<PlaybackPositionState>?, fallbackPosition: Long, fallbackDuration: Long) {
    // Only this row observes playback ticks: the bar reads state in the draw phase, the caption recomposes alone.
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = {
                val position = positionState?.value
                val duration = (position?.durationMs?.takeIf { it > 0 } ?: fallbackDuration).coerceAtLeast(1)
                ((position?.positionMs ?: fallbackPosition).toFloat() / duration).coerceIn(0f, 1f)
            },
            modifier = Modifier.weight(1f).height(4.dp),
            color = scheme.primary, trackColor = scheme.onSurface.copy(alpha = 0.10f),
            gapSize = 0.dp, drawStopIndicator = {},
        )
        val position = positionState?.value
        val positionMs = position?.positionMs ?: fallbackPosition
        val durationMs = position?.durationMs?.takeIf { it > 0 } ?: fallbackDuration
        Text(stringResource(R.string.home_resume_position, formatDuration(positionMs.coerceAtLeast(0)), formatDuration(durationMs.coerceAtLeast(0))),
            style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
    }
}
