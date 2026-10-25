package app.echo.android.feature.podcasts

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.podcast.EchoPodcastEpisode
import java.text.DateFormat
import java.util.Date

@Composable
internal fun PodcastEpisodeRow(episode: EchoPodcastEpisode, current: Boolean, playing: Boolean, busy: Boolean, actions: PodcastActions) {
    var menu by remember(episode) { mutableStateOf(false) }
    var expanded by remember(episode.id) { mutableStateOf(false) }
    val date = remember(episode.publishedMs, androidx.compose.ui.platform.LocalConfiguration.current) {
        if (episode.publishedMs > 0) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(episode.publishedMs)) else ""
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.play(episode, false) }, enabled = !busy) {
                EchoIcon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    stringResource(if (playing) R.string.podcast_pause else R.string.podcast_play))
            }
            Column(Modifier.weight(1f)) {
                Text(episode.title, style = MaterialTheme.typography.titleSmall)
                Text(listOfNotNull(date.takeIf { it.isNotBlank() },
                    if (episode.played) stringResource(R.string.podcast_filter_played) else if (episode.positionMs > 0)
                        stringResource(R.string.podcast_resume_position, episode.positionMs / 60_000) else null,
                    if (episode.durationMs > 0) stringResource(R.string.podcast_duration, episode.durationMs / 60_000) else null,
                    if (current) stringResource(R.string.podcast_current) else null).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { menu = true }) { EchoIcon(Icons.Rounded.MoreVert, stringResource(R.string.podcast_more)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.podcast_restart)) }, onClick = { menu = false; actions.play(episode, true) }, enabled = !busy)
                    DropdownMenuItem(text = { Text(stringResource(R.string.podcast_play_next)) }, onClick = { menu = false; actions.enqueue(episode, true) }, enabled = !busy)
                    DropdownMenuItem(text = { Text(stringResource(R.string.podcast_enqueue)) }, onClick = { menu = false; actions.enqueue(episode, false) }, enabled = !busy)
                    DropdownMenuItem(text = { Text(stringResource(if (episode.played) R.string.podcast_mark_unplayed else R.string.podcast_mark_played)) }, onClick = { menu = false; actions.markPlayed(episode) }, enabled = !busy)
                    episode.website?.let { url -> DropdownMenuItem(text = { Text(stringResource(R.string.podcast_source)) }, onClick = { menu = false; actions.openUrl(url) }) }
                }
            }
        }
        if (episode.description.isNotBlank()) {
            TextButton(onClick = { expanded = !expanded }) { Text(stringResource(if (expanded) R.string.podcast_collapse else R.string.podcast_expand)) }
            if (expanded) Text(episode.description, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}
