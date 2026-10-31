package app.echo.android.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import app.echo.android.model.library.*

@Composable
internal fun MusicRelationsResults(actions: MusicRelationsActions, selection: List<EchoMusicRelation>, kind: EchoMusicRelationKind?,
    query: String, onSelect: (EchoMusicRelation) -> Unit, onPlay: (EchoTrack) -> Unit, modifier: Modifier,
    playback: MusicRelationsPlaybackActions? = null) {
    if (kind == null) {
        val flow = remember(actions, selection, query) { actions.tracks(selection, query) }
        val tracks = flow.collectAsLazyPagingItems()
        LazyColumn(modifier.fillMaxWidth()) {
            if (playback != null) item {
                TextButton(enabled = tracks.itemSnapshotList.items.isNotEmpty(), onClick = {
                    playback.playLoaded(tracks.itemSnapshotList.items.take(40))
                }) { Text(stringResource(R.string.music_relation_play_loaded)) }
            }
            items(tracks.itemCount, key = tracks.itemKey { it.id }) { index ->
                tracks[index]?.let { track -> TrackRow(track = track, onClick = { onPlay(track) },
                    onPlayNext = playback?.playNext, onEnqueue = playback?.enqueue, showAudioInfoTags = false) }
            }
            item { RelationLoadState(tracks.itemCount, tracks.loadState.refresh, tracks.loadState.append, tracks::retry) }
        }
    } else {
        val flow = remember(actions, kind, selection, query) { actions.entities(kind, selection, query) }
        val entities = flow.collectAsLazyPagingItems()
        LazyColumn(modifier.fillMaxWidth()) {
            items(entities.itemCount, key = entities.itemKey { it.key }) { index -> entities[index]?.let { entity ->
                Column(Modifier.fillMaxWidth().clickable { onSelect(EchoMusicRelation(kind, entity.key, entity.title, entity.subtitle)) }
                    .heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text(entity.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    if (entity.subtitle.isNotBlank()) Text(entity.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(pluralStringResource(R.plurals.music_relation_track_count, entity.trackCount, entity.trackCount),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } }
            item { RelationLoadState(entities.itemCount, entities.loadState.refresh, entities.loadState.append, entities::retry) }
        }
    }
}

@Composable
private fun RelationLoadState(count: Int, refresh: LoadState, append: LoadState, retry: () -> Unit) {
    when {
        refresh is LoadState.Loading || append is LoadState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
        refresh is LoadState.Error || append is LoadState.Error -> TextButton(onClick = retry) { Text(stringResource(R.string.music_relation_retry)) }
        count == 0 -> Text(stringResource(R.string.music_relation_empty), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
