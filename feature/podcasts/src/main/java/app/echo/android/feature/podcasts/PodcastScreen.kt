package app.echo.android.feature.podcasts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.podcast.*

@Composable
fun PodcastScreen(
    state: PodcastScreenState, actions: PodcastActions, onBack: () -> Unit,
    currentId: String?, isPlaying: Boolean, playerStrip: @Composable () -> Unit,
    playbackPending: Boolean = false,
) {
    var editor by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var confirmUnfollow by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (!wide && state.selected != null) actions.select(null) else onBack() }) {
                    EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.podcast_back))
                }
                Text(stringResource(R.string.podcast_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = { editor = true }, enabled = !state.busy) {
                    EchoIcon(Icons.Rounded.Add, stringResource(R.string.podcast_subscribe))
                }
            }
            if (state.busy || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.failed) Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.podcast_error), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = actions.retry, enabled = !state.busy) { Text(stringResource(R.string.podcast_retry)) }
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                if (wide || state.selected == null) PodcastSubscriptions(state, actions, onAdd = { editor = true },
                    modifier = if (wide) Modifier.width(248.dp).fillMaxHeight() else Modifier.fillMaxSize(), compact = wide)
                if (state.selected != null) {
                    val feed = state.selected
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    LaunchedEffect(feed.id, state.episodeQuery, state.filter, state.episodes.page) { listState.scrollToItem(0) }
                    LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item(key = "header:${feed.id}") { PodcastShowHeader(feed, state.busy, state.artworkRevision, actions, onUnfollow = { confirmUnfollow = true }) }
                        item(key = "search:${feed.id}") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(pluralStringResource(R.plurals.podcast_episode_count, state.episodes.total, state.episodes.total),
                                    Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                                IconButton(onClick = { searching = !searching; if (!searching) actions.searchEpisodes("") }) {
                                    EchoIcon(Icons.Rounded.Search, stringResource(R.string.podcast_search_episodes))
                                }
                            }
                            if (searching || state.episodeQuery.isNotBlank()) OutlinedTextField(state.episodeQuery, actions.searchEpisodes, Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.podcast_search_episodes)) }, singleLine = true,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PodcastListenFilter.entries.forEach { filter -> FilterChip(state.filter == filter, onClick = { actions.filter(filter) }, border = null,
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest, selectedLabelColor = MaterialTheme.colorScheme.onSurface), label = { Text(stringResource(filter.labelResource())) }) }
                            }
                        }
                        items(state.episodes.episodes, key = { it.id }) { episode ->
                            PodcastEpisodeRow(episode, currentId == episode.id, isPlaying && currentId == episode.id,
                                state.busy, actions, pending = playbackPending && currentId == episode.id,
                                playerControls = { if (currentId == episode.id) playerStrip() })
                        }
                        if (state.loading && state.episodes.episodes.isEmpty()) item("loading") {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                        }
                        if (!state.loading && !state.failed && state.episodes.episodes.isEmpty()) item("empty") {
                            PodcastEmptyResults(onReset = { actions.searchEpisodes(""); actions.filter(PodcastListenFilter.All) })
                        }
                        item(key = "pagination:${feed.id}") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(enabled = state.episodes.page > 0 && !state.loading, onClick = { actions.page(state.episodes.page - 1) }) { Text(stringResource(R.string.podcast_previous)) }
                                Text(stringResource(R.string.podcast_page, state.episodes.page + 1, pluralStringResource(R.plurals.podcast_episode_count, state.episodes.total, state.episodes.total)), style = MaterialTheme.typography.labelSmall)
                                TextButton(enabled = state.episodes.hasNext && !state.loading, onClick = { actions.page(state.episodes.page + 1) }) { Text(stringResource(R.string.podcast_next)) }
                            }
                        }
                    }
                } else if (wide) Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    PodcastWelcome(onAdd = { editor = true })
                }
            }
        }
    }
    if (editor) PodcastSubscribeDialog(actions.subscribe) { editor = false }
    if (confirmUnfollow) AlertDialog(onDismissRequest = { confirmUnfollow = false },
        title = { Text(stringResource(R.string.podcast_unfollow)) }, text = { Text(stringResource(R.string.podcast_unfollow_notice)) },
        confirmButton = { TextButton(onClick = { confirmUnfollow = false; actions.unfollow() }) { Text(stringResource(R.string.podcast_unfollow)) } },
        dismissButton = { TextButton(onClick = { confirmUnfollow = false }) { Text(stringResource(R.string.podcast_cancel)) } })
}

internal fun PodcastListenFilter.labelResource() = when (this) {
    PodcastListenFilter.All -> R.string.podcast_filter_all
    PodcastListenFilter.Unfinished -> R.string.podcast_filter_unfinished
    PodcastListenFilter.InProgress -> R.string.podcast_filter_progress
    PodcastListenFilter.Played -> R.string.podcast_filter_played
}
