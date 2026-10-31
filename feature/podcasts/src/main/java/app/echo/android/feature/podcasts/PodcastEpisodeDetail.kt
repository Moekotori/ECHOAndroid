package app.echo.android.feature.podcasts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.podcast.EchoPodcastEpisode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PodcastEpisodeDetail(episode: EchoPodcastEpisode, date: String, current: Boolean,
    busy: Boolean, pending: Boolean, actions: PodcastActions, playerControls: @Composable () -> Unit, onDismiss: () -> Unit) {
    val windowHeight = with(androidx.compose.ui.platform.LocalDensity.current) { androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = windowHeight * .85f),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.podcast_episode_details), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = onDismiss) { EchoIcon(Icons.Rounded.Close, stringResource(R.string.podcast_close_details)) }
                }
                Text(episode.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(date.takeIf { it.isNotBlank() }, episode.durationMs.takeIf { it > 0 }?.let(::podcastTime)).joinToString(" · "),
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                if (current) playerControls() else FilledTonalButton(onClick = { actions.play(episode, false) }, enabled = !busy && !pending) {
                    EchoIcon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.podcast_play))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(modifier = Modifier.weight(1f), enabled = !busy, onClick = { actions.enqueue(episode, true) }) { Text(stringResource(R.string.podcast_play_next)) }
                    OutlinedButton(modifier = Modifier.weight(1f), enabled = !busy, onClick = { actions.enqueue(episode, false) }) { Text(stringResource(R.string.podcast_enqueue)) }
                }
            }
            if (episode.description.isNotBlank()) item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(episode.description, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                TextButton(enabled = !busy, onClick = { actions.markPlayed(episode) }) {
                    Text(stringResource(if (episode.played) R.string.podcast_mark_unplayed else R.string.podcast_mark_played))
                }
                episode.website?.let { url -> TextButton(onClick = { actions.openUrl(url) }) { Text(stringResource(R.string.podcast_source)) } }
            }
        }
    }
}
