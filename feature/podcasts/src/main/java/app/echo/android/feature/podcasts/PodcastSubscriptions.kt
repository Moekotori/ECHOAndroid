package app.echo.android.feature.podcasts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.*
import app.echo.android.model.podcast.EchoPodcastFeed

@Composable
internal fun PodcastSubscriptions(state: PodcastScreenState, actions: PodcastActions, onAdd: () -> Unit, modifier: Modifier, compact: Boolean) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(state.feedQuery, actions.searchFeeds, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.podcast_search_feeds)) }, shape = RoundedCornerShape(18.dp))
        if (compact) LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(state.visibleFeeds, key = { it.id }) { feed ->
                Card(onClick = { actions.select(feed) }, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(
                    containerColor = if (state.selected?.id == feed.id) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PodcastCover(feed, Modifier.size(48.dp), if (state.selected?.id == feed.id) state.artworkRevision else 0)
                        Text(feed.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            if (state.feeds.isEmpty() && !state.loading) item { TextButton(onClick = onAdd) { Text(stringResource(R.string.podcast_subscribe)) } }
        } else LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.weight(1f), contentPadding = PaddingValues(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.feeds.isEmpty() && !state.loading) item(span = { GridItemSpan(maxLineSpan) }) { PodcastWelcome(onAdd) }
            else item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.podcast_collection), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            items(state.visibleFeeds, key = { it.id }) { feed ->
                Card(onClick = { actions.select(feed) }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PodcastCover(feed, Modifier.fillMaxWidth().aspectRatio(1f), state.artworkRevision)
                        Text(feed.title, Modifier.heightIn(min = 40.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(feed.author.ifBlank { stringResource(R.string.podcast_title) }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (state.feeds.isNotEmpty() && state.visibleFeeds.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { PodcastEmptyResults { actions.searchFeeds("") } }
        }
    }
}

@Composable
internal fun PodcastCover(feed: EchoPodcastFeed, modifier: Modifier, revision: Int = 0) {
    key(feed.id, revision) {
        EchoArtworkImage(feed.artworkUrl, null, modifier, shape = RoundedCornerShape(18.dp), sizeClass = EchoArtworkSize.Card,
            placeholder = {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Box(contentAlignment = Alignment.Center) { EchoIcon(Icons.Rounded.Podcasts, null, Modifier.fillMaxSize(.4f)) }
                }
            })
    }
}

@Composable
internal fun PodcastWelcome(onAdd: () -> Unit) {
    val decoration = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .07f)
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.matchParentSize()) { repeat(5) {
                drawCircle(decoration, size.width * (.2f + it * .13f), Offset(size.width, 0f), style = Stroke(1.dp.toPx()))
            } }
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                EchoIcon(Icons.Rounded.Podcasts, null, Modifier.size(44.dp))
                Text(stringResource(R.string.podcast_welcome), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.podcast_welcome_detail), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = onAdd) { EchoIcon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.podcast_subscribe)) }
            }
        }
    }
}

@Composable
internal fun PodcastEmptyResults(onReset: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EchoIcon(Icons.Rounded.SearchOff, null, Modifier.size(32.dp))
        Text(stringResource(R.string.podcast_no_results), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.podcast_no_results_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onReset) { Text(stringResource(R.string.podcast_reset_search)) }
    }
}
