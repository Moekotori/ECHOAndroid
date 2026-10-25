package app.echo.android.feature.podcasts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoIcon
import app.echo.android.model.podcast.*

data class PodcastScreenState(
    val feeds: List<EchoPodcastFeed> = emptyList(), val visibleFeeds: List<EchoPodcastFeed> = emptyList(),
    val selected: EchoPodcastFeed? = null, val episodes: PodcastEpisodePage = PodcastEpisodePage(),
    val feedQuery: String = "", val episodeQuery: String = "", val filter: PodcastListenFilter = PodcastListenFilter.All,
    val loading: Boolean = false, val busy: Boolean = false, val failed: Boolean = false,
)

class PodcastActions(
    val select: (EchoPodcastFeed?) -> Unit,
    val searchFeeds: (String) -> Unit,
    val searchEpisodes: (String) -> Unit,
    val filter: (PodcastListenFilter) -> Unit,
    val page: (Int) -> Unit,
    val subscribe: suspend (String) -> Unit,
    val refresh: () -> Unit,
    val unfollow: () -> Unit,
    val retry: () -> Unit,
    val listen: () -> Unit,
    val play: (EchoPodcastEpisode, Boolean) -> Unit,
    val enqueue: (EchoPodcastEpisode, Boolean) -> Unit,
    val markPlayed: (EchoPodcastEpisode) -> Unit,
    val openUrl: (String) -> Unit,
)

@Composable
fun PodcastScreen(
    state: PodcastScreenState, actions: PodcastActions, onBack: () -> Unit,
    currentId: String?, isPlaying: Boolean, playerStrip: @Composable () -> Unit,
) {
    var editor by rememberSaveable { mutableStateOf(false) }
    var confirmUnfollow by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (state.selected != null) actions.select(null) else onBack() }) {
                EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.podcast_back))
            }
            Text(stringResource(R.string.podcast_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = { editor = true }, enabled = !state.busy) {
                EchoIcon(Icons.Rounded.Add, stringResource(R.string.podcast_subscribe))
            }
        }
        if (state.busy || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (state.failed) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.podcast_error), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = actions.retry, enabled = !state.busy) { Text(stringResource(R.string.podcast_retry)) }
        }
        if (state.selected == null) {
            OutlinedTextField(state.feedQuery, actions.searchFeeds, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.podcast_search_feeds)) }, singleLine = true)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (state.feeds.isEmpty() && !state.loading) item {
                    Column(Modifier.padding(vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        EchoIcon(Icons.Rounded.Podcasts, null, Modifier.size(48.dp))
                        Text(stringResource(R.string.podcast_welcome), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.podcast_notice))
                        Button(onClick = { editor = true }) { Text(stringResource(R.string.podcast_subscribe)) }
                    }
                }
                items(state.visibleFeeds, key = { it.id }) { feed ->
                    ListItem(
                        modifier = Modifier.clickable { actions.select(feed) },
                        leadingContent = { EchoArtworkImage(feed.artworkUrl, null, Modifier.size(56.dp)) },
                        headlineContent = { Text(feed.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(feed.author, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
                if (state.feeds.isNotEmpty() && state.visibleFeeds.isEmpty()) item { Text(stringResource(R.string.podcast_empty)) }
            }
        } else {
            val feed = state.selected
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "header:${feed.id}") { PodcastShowHeader(feed, state.busy, actions, onUnfollow = { confirmUnfollow = true }) }
                item(key = "search:${feed.id}") {
                    OutlinedTextField(state.episodeQuery, actions.searchEpisodes, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.podcast_search_episodes)) }, singleLine = true)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PodcastListenFilter.entries.forEach { filter ->
                            FilterChip(selected = state.filter == filter, onClick = { actions.filter(filter) }, label = { Text(stringResource(filter.labelResource())) })
                        }
                    }
                }
                items(state.episodes.episodes, key = { it.id }) { episode ->
                    PodcastEpisodeRow(episode, currentId == episode.id, isPlaying && currentId == episode.id, state.busy, actions)
                }
                if (!state.loading && state.episodes.episodes.isEmpty()) item { Text(stringResource(R.string.podcast_empty), Modifier.padding(16.dp)) }
                item(key = "pagination:${feed.id}") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(enabled = state.episodes.page > 0 && !state.loading, onClick = { actions.page(state.episodes.page - 1) }) { Text(stringResource(R.string.podcast_previous)) }
                        Text(stringResource(R.string.podcast_page, state.episodes.page + 1,
                            pluralStringResource(R.plurals.podcast_episode_count, state.episodes.total, state.episodes.total)))
                        TextButton(enabled = state.episodes.hasNext && !state.loading, onClick = { actions.page(state.episodes.page + 1) }) { Text(stringResource(R.string.podcast_next)) }
                    }
                }
            }
        }
        playerStrip()
    }
    if (editor) PodcastSubscribeDialog(actions.subscribe) { editor = false }
    if (confirmUnfollow) AlertDialog(onDismissRequest = { confirmUnfollow = false },
        title = { Text(stringResource(R.string.podcast_unfollow)) },
        text = { Text(stringResource(R.string.podcast_unfollow_notice)) },
        confirmButton = { TextButton(onClick = { confirmUnfollow = false; actions.unfollow() }) { Text(stringResource(R.string.podcast_unfollow)) } },
        dismissButton = { TextButton(onClick = { confirmUnfollow = false }) { Text(stringResource(R.string.podcast_cancel)) } })
}

internal fun PodcastListenFilter.labelResource() = when (this) {
    PodcastListenFilter.All -> R.string.podcast_filter_all
    PodcastListenFilter.Unfinished -> R.string.podcast_filter_unfinished
    PodcastListenFilter.InProgress -> R.string.podcast_filter_progress
    PodcastListenFilter.Played -> R.string.podcast_filter_played
}

@Composable
private fun PodcastShowHeader(feed: EchoPodcastFeed, busy: Boolean, actions: PodcastActions, onUnfollow: () -> Unit) {
    var expanded by rememberSaveable(feed.id) { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EchoArtworkImage(feed.artworkUrl, null, Modifier.size(80.dp), sizeClass = app.echo.android.design.EchoArtworkSize.Card)
            Column(Modifier.weight(1f)) {
                Text(feed.title, style = MaterialTheme.typography.titleLarge)
                Text(feed.author, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (feed.description.isNotBlank()) {
            Text(feed.description, maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { expanded = !expanded }) { Text(stringResource(if (expanded) R.string.podcast_collapse else R.string.podcast_expand)) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Button(onClick = actions.listen, enabled = !busy) { Text(stringResource(R.string.podcast_listen)) }
            IconButton(onClick = actions.refresh, enabled = !busy) { EchoIcon(Icons.Rounded.Refresh, stringResource(R.string.podcast_refresh)) }
            IconButton(onClick = onUnfollow, enabled = !busy) { EchoIcon(Icons.Rounded.DeleteOutline, stringResource(R.string.podcast_unfollow)) }
            feed.website?.let { url -> IconButton(onClick = { actions.openUrl(url) }) { EchoIcon(Icons.Rounded.OpenInNew, stringResource(R.string.podcast_source)) } }
        }
    }
}
