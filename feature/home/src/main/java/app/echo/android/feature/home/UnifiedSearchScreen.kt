package app.echo.android.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import app.echo.android.design.EchoIcon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.echo.android.design.displayAlbum
import app.echo.android.design.displayArtist
import app.echo.android.design.displayTrack
import app.echo.android.model.library.*
import app.echo.android.model.connect.EchoRemoteTrack

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnifiedSearchScreen(
    query: String, category: UnifiedSearchCategory,
    local: UnifiedSearchSourceState, pc: UnifiedSearchSourceState,
    onQuery: (String) -> Unit, onCategory: (UnifiedSearchCategory) -> Unit,
    onBack: () -> Unit, onMoments: () -> Unit, onConnect: () -> Unit,
    onRetryLocal: () -> Unit, onRetryPc: () -> Unit,
    onTrack: (EchoTrack) -> Unit, onAlbum: (AlbumSummary) -> Unit, onArtist: (ArtistSummary) -> Unit,
    onPlaylist: (EchoPlaylist) -> Unit, onMoment: (EchoSavedMoment) -> Unit, onPcTrack: (EchoRemoteTrack) -> Unit,
    onViewAll: (UnifiedSearchSection) -> Unit,
    onTrackAction: (EchoTrack, SearchTrackAction) -> Unit,
    onPcAction: (EchoRemoteTrack, SearchTrackAction) -> Unit,
    requestFocus: Boolean = true,
    onFocusRequested: () -> Unit = {},
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val requester = remember { FocusRequester() }
    LaunchedEffect(requestFocus) { if (requestFocus) { requester.requestFocus(); onFocusRequested() } }
    val listState = rememberLazyListState()
    var previousQuery by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(query) }
    var previousCategory by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(category) }
    LaunchedEffect(query, category) {
        if (previousQuery != query || previousCategory != category) listState.scrollToItem(0)
        previousQuery = query; previousCategory = category
    }
    fun finishInput(action: () -> Unit) { focus.clearFocus(); keyboard?.hide(); action() }
    val showLocal = category != UnifiedSearchCategory.Pc
    val showPc = category != UnifiedSearchCategory.Library
    val results = local.results
    val searching = query.isNotBlank()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { finishInput(onBack) }) { Text(stringResource(R.string.search_back)) }
            OutlinedTextField(query, onQuery, label = { Text(stringResource(R.string.search_everywhere)) },
                singleLine = true, modifier = Modifier.weight(1f).focusRequester(requester),
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                    EchoIcon(Icons.Rounded.Close, stringResource(R.string.search_clear))
                } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { finishInput {} }))
        }
        FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UnifiedSearchCategory.entries.forEach { item ->
                FilterChip(category == item, { finishInput { onCategory(item) } }, label = {
                    Text(stringResource(when (item) {
                        UnifiedSearchCategory.All -> R.string.search_all
                        UnifiedSearchCategory.Library -> R.string.search_library
                        UnifiedSearchCategory.Pc -> R.string.search_pc
                    }))
                })
            }
            TextButton(onClick = { finishInput(onMoments) }) { Text(stringResource(R.string.moments_title)) }
        }
        LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (category == UnifiedSearchCategory.Pc && pc.phase == UnifiedSearchPhase.Disconnected) item("pc-disconnected") {
                SearchSourceNotice(stringResource(R.string.search_pc_disconnected), stringResource(R.string.search_connect_pc)) {
                    finishInput(onConnect)
                }
            }
            if (searching && showLocal) {
                when (local.phase) {
                    UnifiedSearchPhase.Idle, UnifiedSearchPhase.Loading -> item("local-loading") { SearchLoading(R.string.search_loading_library) }
                    UnifiedSearchPhase.Failed -> item("local-error") {
                        SearchSourceNotice(stringResource(R.string.search_library_failed), stringResource(R.string.search_retry), onRetryLocal)
                    }
                    else -> Unit
                }
                if (results.moments.isNotEmpty()) item { SearchGroupLabel(UnifiedSearchSection.Moments, onViewAll) }
                items(results.moments, key = { "moment:${it.bookmark.id}" }) { moment ->
                    SearchEntry(moment.bookmark.label, "${displayTrack(moment.track.title)} · ${displayArtist(moment.track.artist)}") { finishInput { onMoment(moment) } }
                }
                if (results.playlists.isNotEmpty()) item { SearchGroupLabel(UnifiedSearchSection.Playlists, onViewAll) }
                items(results.playlists, key = { "playlist:${it.id}" }) { SearchEntry(it.name, stringResource(R.string.search_library)) { finishInput { onPlaylist(it) } } }
                if (results.tracks.isNotEmpty()) item { SearchGroupLabel(UnifiedSearchSection.Tracks, onViewAll) }
                items(results.tracks, key = { "track:${it.id}" }) { track ->
                    val origin = stringResource(if (track.source.isLocalAudioFile) R.string.search_phone else R.string.search_remote)
                    SearchEntry(displayTrack(track.title), "${displayArtist(track.artist)} · $origin", track,
                        onTrackAction = { selected, action -> finishInput { onTrackAction(selected, action) } }) { finishInput { onTrack(track) } }
                }
                if (results.albums.isNotEmpty()) item { SearchGroupLabel(UnifiedSearchSection.Albums, onViewAll) }
                items(results.albums, key = { "album:${it.albumKey}" }) { SearchEntry(displayAlbum(it.title), displayArtist(it.albumArtist ?: it.artist)) { finishInput { onAlbum(it) } } }
                if (results.artists.isNotEmpty()) item { SearchGroupLabel(UnifiedSearchSection.Artists, onViewAll) }
                items(results.artists, key = { "artist:${it.artistKey}" }) { SearchEntry(displayArtist(it.name), stringResource(R.string.search_library)) { finishInput { onArtist(it) } } }
                if (local.phase == UnifiedSearchPhase.Ready && results.tracks.isEmpty() && results.albums.isEmpty() &&
                    results.artists.isEmpty() && results.playlists.isEmpty() && results.moments.isEmpty()) item("local-empty") {
                    Text(stringResource(R.string.search_empty_library), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (searching && showPc) {
                when (pc.phase) {
                    UnifiedSearchPhase.Idle, UnifiedSearchPhase.Loading -> item("pc-loading") { SearchLoading(R.string.search_loading_pc) }
                    UnifiedSearchPhase.Failed -> item("pc-error") {
                        SearchSourceNotice(stringResource(R.string.search_pc_failed), stringResource(R.string.search_retry), onRetryPc)
                    }
                    UnifiedSearchPhase.Ready -> {
                        if (pc.results.pcTracks.isEmpty()) item("pc-empty") {
                            Text(stringResource(R.string.search_empty_pc), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            item { SearchGroupLabel(UnifiedSearchSection.Pc, onViewAll) }
                            items(pc.results.pcTracks, key = { "pc:${it.id}" }) {
                                SearchEntry(displayTrack(it.title), displayArtist(it.artist), pcTrack = it,
                                    onPcAction = { track, action -> finishInput { onPcAction(track, action) } }) { finishInput { onPcTrack(it) } }
                            }
                        }
                    }
                    UnifiedSearchPhase.Disconnected -> Unit
                }
            }
            if (!searching) item { Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (category == UnifiedSearchCategory.All && pc.phase == UnifiedSearchPhase.Disconnected) item("pc-optional") {
                TextButton(onClick = { finishInput(onConnect) }) { Text(stringResource(R.string.search_connect_pc)) }
            }
        }
    }
}

@Composable private fun SearchGroupLabel(section: UnifiedSearchSection, onViewAll: (UnifiedSearchSection) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(section.titleRes), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = { onViewAll(section) }) { Text(stringResource(R.string.search_view_all)) }
    }
}
@Composable private fun SearchLoading(label: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
@Composable internal fun SearchSourceNotice(text: String, action: String, onAction: () -> Unit) {
    Column {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onAction) { Text(action) }
    }
}
@Composable internal fun SearchEntry(title: String, subtitle: String, track: EchoTrack? = null,
    onTrackAction: (EchoTrack, SearchTrackAction) -> Unit = { _, _ -> },
    pcTrack: EchoRemoteTrack? = null, onPcAction: (EchoRemoteTrack, SearchTrackAction) -> Unit = { _, _ -> }, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f).clickable(onClick = onClick).heightIn(min = 48.dp).padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (track != null) SearchTrackActions { onTrackAction(track, it) }
    if (pcTrack != null) SearchTrackActions(phoneOnly = true) { onPcAction(pcTrack, it) }
    }
}
