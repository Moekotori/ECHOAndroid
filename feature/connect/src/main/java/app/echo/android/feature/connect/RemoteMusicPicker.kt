package app.echo.android.feature.connect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import app.echo.android.model.connect.EchoLinkLibraryQueryPolicy
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.echo.android.model.connect.EchoRemoteLibraryState
import app.echo.android.model.connect.EchoRemoteTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemoteMusicPicker(
    initialQueue: Boolean,
    library: EchoRemoteLibraryState,
    queueItems: List<EchoRemoteTrack>,
    currentTrackId: String?,
    pcTitle: String,
    remoteError: String?,
    onSearchLibrary: (String) -> Unit,
    onLoadMoreLibrary: () -> Unit = {},
    onPlayTrack: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showQueue by rememberSaveable { mutableStateOf(initialQueue) }
    var query by rememberSaveable { mutableStateOf("") }
    val search = query.trim()
    val searchLibrary by rememberUpdatedState(onSearchLibrary)
    var requestedQuery by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(search, showQueue) {
        if (!showQueue) {
            if (search.isNotEmpty()) delay(300)
            requestedQuery = search
            searchLibrary(search)
        }
    }
    val source = if (showQueue) queueItems else library.tracks
    // Filtering is cancellable and off the UI thread, including for a large PC queue.
    var rows by remember(source, search, showQueue) { mutableStateOf<List<IndexedValue<EchoRemoteTrack>>?>(null) }
    LaunchedEffect(source, search, showQueue) {
        rows = withContext(Dispatchers.Default) {
            buildList {
                source.forEachIndexed { index, track ->
                    ensureActive()
                    if (search.isEmpty() || track.title.contains(search, true) ||
                        track.artist.contains(search, true) || track.album.orEmpty().contains(search, true)) {
                        add(IndexedValue(index, track))
                    }
                }
            }
        }
    }
    val awaitingQuery = !showQueue && (requestedQuery != search || library.query.trim() != search)
    val loading = rows == null || (!showQueue && (awaitingQuery || library.isLoading || library.isLoadingMore))
    val visibleRows = if (awaitingQuery) emptyList() else rows.orEmpty()
    val listState = rememberLazyListState()
    val loadMore = rememberUpdatedState(onLoadMoreLibrary)
    val pageableCount = if (showQueue || visibleRows.size < source.size) visibleRows.size else library.totalCount
    LaunchedEffect(listState, visibleRows.size, pageableCount, library.isLoadingMore, showQueue) {
        if (showQueue || library.isLoadingMore || visibleRows.isEmpty() || pageableCount <= visibleRows.size) return@LaunchedEffect
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            EchoLinkLibraryQueryPolicy.shouldRequestNextPage(
                loadedCount = visibleRows.size,
                totalCount = pageableCount,
                lastVisibleIndex = visible.lastOrNull()?.index ?: -1,
                visibleItemCount = visible.size,
                firstVisibleIndex = visible.firstOrNull()?.index ?: 0,
            )
        }.distinctUntilChanged().collect { request ->
            if (request) loadMore.value()
        }
    }
    LaunchedEffect(showQueue, search) { listState.scrollToItem(0) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.remote_choose_music), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.remote_picker_destination, pcTitle),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.remote_close)) }
            }
            SecondaryTabRow(selectedTabIndex = if (showQueue) 1 else 0) {
                Tab(selected = !showQueue, onClick = { showQueue = false }, text = { Text(stringResource(R.string.remote_pc_library)) })
                Tab(selected = showQueue, onClick = { showQueue = true }, text = { Text(stringResource(R.string.feature_connect_pc_queue_2e91c4)) })
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                label = { Text(stringResource(R.string.remote_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton({ query = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.remote_clear_search)) } }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            val error = remoteError ?: if (!showQueue && !awaitingQuery) library.error else null
            if (!error.isNullOrBlank()) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (!showQueue) TextButton({ searchLibrary(search) }) {
                        Text(stringResource(R.string.feature_connect_refresh_828c69))
                    }
                }
            }
            if (visibleRows.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(when {
                        loading -> R.string.remote_loading
                        search.isNotEmpty() -> R.string.remote_no_results
                        showQueue -> R.string.remote_empty_queue
                        else -> R.string.remote_empty_library
                    }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Queue entries may repeat the same track; preserve their original positions.
                    items(visibleRows, key = { "${it.value.id.orEmpty()}:${it.index}" }, contentType = { "pcTrack" }) { row ->
                        RemoteMusicRow(
                            track = row.value,
                            current = !row.value.id.isNullOrBlank() && row.value.id == currentTrackId,
                            onClick = {
                                row.value.id?.takeIf(String::isNotBlank)?.let {
                                    onPlayTrack(it)
                                    onDismiss()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
