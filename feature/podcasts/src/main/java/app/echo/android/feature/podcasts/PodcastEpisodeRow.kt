package app.echo.android.feature.podcasts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.podcast.EchoPodcastEpisode
import java.text.DateFormat
import java.util.Date

@Composable
internal fun PodcastEpisodeRow(episode: EchoPodcastEpisode, current: Boolean, playing: Boolean, busy: Boolean, actions: PodcastActions, pending: Boolean = false, playerControls: @Composable () -> Unit = {}) {
    var menu by remember(episode.id) { mutableStateOf(false) }
    var expanded by rememberSaveable(episode.id) { mutableStateOf(false) }
    val date = remember(episode.publishedMs, androidx.compose.ui.platform.LocalConfiguration.current) {
        if (episode.publishedMs > 0) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(episode.publishedMs)) else ""
    }
    Surface(onClick = { expanded = true }, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        border = if (current) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        color = if (current) MaterialTheme.colorScheme.surfaceContainerLow else androidx.compose.ui.graphics.Color.Transparent) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { actions.play(episode, false) }, enabled = !busy && !pending) {
                if (pending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else EchoIcon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    stringResource(if (playing) R.string.podcast_pause_episode else R.string.podcast_play_episode, episode.title))
            }
            Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(episode.title, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(listOfNotNull(date.takeIf { it.isNotBlank() },
                    if (episode.played) stringResource(R.string.podcast_filter_played) else if (episode.positionMs > 0)
                        stringResource(R.string.podcast_saved_time, podcastTime(episode.positionMs)) else null,
                    if (episode.durationMs > 0) stringResource(R.string.podcast_episode_time, podcastTime(episode.durationMs)) else null,
                    if (playing) stringResource(R.string.podcast_current) else null).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Box {
                IconButton(onClick = { menu = true }) { EchoIcon(Icons.Rounded.MoreVert, stringResource(R.string.podcast_more_for, episode.title)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.podcast_restart)) }, onClick = { menu = false; actions.play(episode, true) }, enabled = !busy)
                    DropdownMenuItem(text = { Text(stringResource(R.string.podcast_play_next)) }, onClick = { menu = false; actions.enqueue(episode, true) }, enabled = !busy)
                    DropdownMenuItem(text = { Text(stringResource(R.string.podcast_enqueue)) }, onClick = { menu = false; actions.enqueue(episode, false) }, enabled = !busy)
                    DropdownMenuItem(text = { Text(stringResource(if (episode.played) R.string.podcast_mark_unplayed else R.string.podcast_mark_played)) }, onClick = { menu = false; actions.markPlayed(episode) }, enabled = !busy)
                    episode.website?.let { url -> DropdownMenuItem(text = { Text(stringResource(R.string.podcast_source)) }, onClick = { menu = false; actions.openUrl(url) }) }
                }
            }
        }
        if (episode.description.isNotBlank()) Text(episode.description, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        if (!episode.played && episode.positionMs > 0 && episode.durationMs > 0) {
            LinearProgressIndicator(progress = { (episode.positionMs.toDouble() / episode.durationMs).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp))
        }
    }
    }
    if (expanded) PodcastEpisodeDetail(episode, date, current, busy, pending, actions,
        playerControls = playerControls, onDismiss = { expanded = false })
}

internal fun podcastTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
