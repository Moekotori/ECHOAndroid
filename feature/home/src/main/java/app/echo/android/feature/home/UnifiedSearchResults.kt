package app.echo.android.feature.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import app.echo.android.design.displayAlbum
import app.echo.android.design.displayArtist
import app.echo.android.design.displayTrack
import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.library.*

enum class UnifiedSearchSection(val titleRes: Int) {
    Tracks(R.string.search_tracks), Albums(R.string.search_albums), Artists(R.string.search_artists),
    Playlists(R.string.search_playlists), Moments(R.string.moments_title), Pc(R.string.search_pc),
}

sealed interface UnifiedSearchItem {
    val key: String
    data class Track(val value: EchoTrack) : UnifiedSearchItem { override val key get() = "track:${value.id}" }
    data class Album(val value: AlbumSummary) : UnifiedSearchItem { override val key get() = "album:${value.albumKey}" }
    data class Artist(val value: ArtistSummary) : UnifiedSearchItem { override val key get() = "artist:${value.artistKey}" }
    data class Playlist(val value: EchoPlaylist) : UnifiedSearchItem { override val key get() = "playlist:${value.id}" }
    data class Moment(val value: EchoSavedMoment) : UnifiedSearchItem { override val key get() = "moment:${value.bookmark.id}" }
    data class PcTrack(val value: EchoRemoteTrack) : UnifiedSearchItem { override val key get() = "pc:${value.id}" }
}

@Composable
fun UnifiedSearchResults(
    section: UnifiedSearchSection, query: String, items: LazyPagingItems<UnifiedSearchItem>,
    onBack: () -> Unit, onOpen: (UnifiedSearchItem) -> Unit,
    onTrackAction: (EchoTrack, SearchTrackAction) -> Unit,
    onPcAction: (EchoRemoteTrack, SearchTrackAction) -> Unit,
) {
    val state = rememberLazyListState()
    var previousQuery by rememberSaveable { mutableStateOf(query) }
    LaunchedEffect(query) {
        if (previousQuery != query) state.scrollToItem(0)
        previousQuery = query
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.search_back)) }
            Column(Modifier.weight(1f)) {
                Text(stringResource(section.titleRes), style = MaterialTheme.typography.titleLarge)
                Text(query, style = MaterialTheme.typography.bodyMedium)
            }
        }
        LazyColumn(state = state, modifier = Modifier.weight(1f), contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (items.loadState.refresh is LoadState.Loading && items.itemCount == 0) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (items.loadState.refresh is LoadState.Error) item {
                SearchSourceNotice(stringResource(R.string.search_results_failed), stringResource(R.string.search_retry), items::retry)
            }
            if (items.itemCount == 0 && items.loadState.refresh is LoadState.NotLoading) item { Text(stringResource(R.string.search_empty_library)) }
            items(items.itemCount, key = { items.peek(it)?.key ?: "placeholder:$it" }) { index ->
                items[index]?.let { item ->
                    val (title, subtitle) = when (item) {
                        is UnifiedSearchItem.Track -> displayTrack(item.value.title) to displayArtist(item.value.artist)
                        is UnifiedSearchItem.Album -> displayAlbum(item.value.title) to displayArtist(item.value.albumArtist ?: item.value.artist)
                        is UnifiedSearchItem.Artist -> displayArtist(item.value.name) to stringResource(R.string.search_library)
                        is UnifiedSearchItem.Playlist -> item.value.name to stringResource(R.string.search_playlists)
                        is UnifiedSearchItem.Moment -> item.value.bookmark.label to displayTrack(item.value.track.title)
                        is UnifiedSearchItem.PcTrack -> displayTrack(item.value.title) to displayArtist(item.value.artist)
                    }
                    SearchEntry(title, subtitle, track = (item as? UnifiedSearchItem.Track)?.value,
                        onTrackAction = onTrackAction, pcTrack = (item as? UnifiedSearchItem.PcTrack)?.value,
                        onPcAction = onPcAction) { onOpen(item) }
                }
            }
            when (items.loadState.append) {
                is LoadState.Loading -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                is LoadState.Error -> item { SearchSourceNotice(stringResource(R.string.search_results_failed), stringResource(R.string.search_retry), items::retry) }
                else -> Unit
            }
        }
    }
}
