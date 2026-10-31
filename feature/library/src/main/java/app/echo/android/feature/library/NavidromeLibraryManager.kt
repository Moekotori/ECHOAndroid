package app.echo.android.feature.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoModalSheet
import app.echo.android.model.library.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavidromeLibraryManager(actions: RemoteLibraryWritebackActions, onDismiss: () -> Unit) {
    val server = actions.navidrome ?: return RemotePlaylistManager(actions, onDismiss)
    var query by remember(server) { mutableStateOf<NavidromeQuery>(NavidromeQuery.Collection(NavidromeCollection.Favorites)) }
    var history by remember(server) { mutableStateOf<List<NavidromeQuery>>(emptyList()) }
    var search by remember(server) { mutableStateOf("") }
    var revision by remember(server) { mutableIntStateOf(0) }
    var page by remember(server) { mutableStateOf<NavidromePage?>(null) }
    var loading by remember(server) { mutableStateOf(false) }
    var busy by remember(server) { mutableStateOf(false) }
    var failed by remember(server) { mutableStateOf(false) }
    var saved by remember(server) { mutableStateOf(false) }
    var detail by remember(server) { mutableStateOf<NavidromeEntry?>(null) }
    var playlists by remember(server) { mutableStateOf(false) }
    var radioEditor by remember(server) { mutableStateOf<NavidromeEntry?>(null) }
    var newRadio by remember(server) { mutableStateOf(false) }
    var receipts by remember(server) { mutableStateOf(false) }
    var confirmRestore by remember(server) { mutableStateOf(false) }
    var bookmark by remember(server) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun perform(operation: suspend () -> Unit) {
        if (busy) return
        busy = true; failed = false; saved = false
        scope.launch {
            try { operation(); saved = true; revision++ }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    LaunchedEffect(query, revision, server) {
        loading = true; failed = false; page = null
        try { page = server.load(query) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    LaunchedEffect(search) {
        if (search.isNotBlank()) { delay(300); query = NavidromeQuery.Search(search.trim()) }
    }
    EchoModalSheet(onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !busy })) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Navidrome", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { revision++ }, enabled = !busy && !loading) { Text(stringResource(R.string.nav_refresh)) }
                TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.remote_playlist_close)) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavidromeCollection.entries.forEach { collection ->
                    FilterChip(selected = (query as? NavidromeQuery.Collection)?.collection == collection,
                        onClick = { search = ""; history = emptyList(); query = NavidromeQuery.Collection(collection) },
                        label = { Text(stringResource(collection.label())) }, enabled = !busy)
                }
            }
            OutlinedTextField(search, { search = it.take(500); history = emptyList()
                if (search.isBlank()) query = NavidromeQuery.Collection(NavidromeCollection.Favorites)
            }, singleLine = true, enabled = !busy,
                label = { Text(stringResource(R.string.nav_search)) }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { playlists = true }, enabled = !busy) { Text(stringResource(R.string.remote_playlist_manage)) }
                TextButton(onClick = { receipts = true }, enabled = !busy) { Text(stringResource(R.string.nav_receipts)) }
            }
            val collection = (query as? NavidromeQuery.Collection)?.collection
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                when (collection) {
                    NavidromeCollection.Favorites -> TextButton(enabled = !busy, onClick = { perform(server.refreshFavorites) }) {
                        Text(stringResource(R.string.nav_sync_favorites))
                    }
                    NavidromeCollection.Bookmarks -> TextButton(enabled = !busy, onClick = { bookmark = true }) {
                        Text(stringResource(R.string.nav_bookmark_current))
                    }
                    NavidromeCollection.Radios -> TextButton(enabled = !busy, onClick = { newRadio = true }) {
                        Text(stringResource(R.string.nav_radio_add))
                    }
                    NavidromeCollection.Queue -> {
                        TextButton(enabled = !busy, onClick = { perform(server.saveQueue) }) { Text(stringResource(R.string.nav_queue_save)) }
                        TextButton(enabled = !busy && page?.entries?.isNotEmpty() == true, onClick = { confirmRestore = true }) {
                            Text(stringResource(R.string.nav_queue_restore))
                        }
                    }
                    else -> Unit
                }
            }
            if (collection == NavidromeCollection.Queue) Text(stringResource(R.string.nav_queue_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (query is NavidromeQuery.Browse) TextButton(onClick = {
                query = history.lastOrNull() ?: NavidromeQuery.Collection(NavidromeCollection.Artists)
                history = history.dropLast(1)
            }) { Text(stringResource(R.string.remote_playlist_back)) }
            if (busy || loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) Text(stringResource(R.string.nav_error), color = MaterialTheme.colorScheme.error)
            if (saved) Text(stringResource(R.string.nav_saved), style = MaterialTheme.typography.bodySmall)
            LazyColumn(Modifier.weight(1f)) {
                if (!loading && page?.entries?.isEmpty() == true) item { Text(stringResource(R.string.nav_empty), Modifier.padding(vertical = 20.dp)) }
                itemsIndexed(page?.entries.orEmpty(), key = { index, entry -> "${entry.kind}:${entry.id}:$index" }) { _, entry ->
                    NavidromeEntryRow(entry, enabled = !busy, onOpen = {
                        when (entry.kind) {
                            NavidromeKind.Artist, NavidromeKind.Album -> {
                                history = (history + query).takeLast(16)
                                query = NavidromeQuery.Browse(entry.kind, entry.id)
                            }
                            else -> perform { server.play(entry) }
                        }
                    }, onDetails = {
                        if (entry.kind == NavidromeKind.Radio) radioEditor = entry else detail = entry
                    })
                    if (collection == NavidromeCollection.Bookmarks) TextButton(enabled = !busy,
                        onClick = { perform { server.execute(NavidromeCommand.DeleteBookmark(entry.id)) } }) {
                        Text(stringResource(R.string.nav_bookmark_delete))
                    }
                }
            }
            val current = page
            if (current != null && (current.page > 0 || current.hasMore)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(enabled = !busy && !loading && current.page > 0, onClick = { query = query.atPage(current.page - 1) }) {
                    Text(stringResource(R.string.nav_previous))
                }
                Text((current.page + 1).toString(), Modifier.align(Alignment.CenterVertically))
                TextButton(enabled = !busy && !loading && current.hasMore, onClick = { query = query.atPage(current.page + 1) }) {
                    Text(stringResource(R.string.nav_next))
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
    if (playlists) RemotePlaylistManager(actions) { playlists = false }
    detail?.let { entry -> NavidromeEntryDialog(server, entry, actions, onChanged = { revision++ }, onDismiss = { detail = null }) }
    if (newRadio || radioEditor != null) NavidromeRadioDialog(server, radioEditor, onChanged = { revision++ }, onDismiss = {
        radioEditor = null; newRadio = false
    })
    if (receipts) NavidromeReceiptsDialog(server) { receipts = false }
    if (confirmRestore) AlertDialog(onDismissRequest = { confirmRestore = false },
        title = { Text(stringResource(R.string.nav_queue_restore)) }, text = { Text(stringResource(R.string.nav_queue_confirm)) },
        confirmButton = { TextButton(onClick = { confirmRestore = false; perform(server.restoreQueue) }) { Text(stringResource(R.string.nav_queue_restore)) } },
        dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text(stringResource(R.string.remote_playlist_close)) } })
    if (bookmark) NavidromeTextDialog(stringResource(R.string.nav_bookmark_current), "", stringResource(R.string.nav_comment),
        onDismiss = { bookmark = false }, onSave = { comment -> server.bookmarkCurrent(comment); revision++; bookmark = false })
}

@Composable
internal fun NavidromeEntryRow(entry: NavidromeEntry, enabled: Boolean, onOpen: () -> Unit, onDetails: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        entry.artworkUri?.let { EchoArtworkImage(it, null, Modifier.size(44.dp)) }
        TextButton(onClick = onOpen, enabled = enabled, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
            Column(Modifier.fillMaxWidth()) {
                Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(stringResource(entry.kind.label()), entry.track?.artist,
                    entry.comment.takeIf(String::isNotBlank), entry.positionMs.takeIf { it > 0 }?.let { "${it / 60_000}:${(it / 1000 % 60).toString().padStart(2, '0')}" })
                    .joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onDetails, enabled = enabled) { EchoIcon(Icons.Rounded.MoreVert, stringResource(R.string.nav_details)) }
    }
}

private fun NavidromeQuery.atPage(page: Int): NavidromeQuery = when (this) {
    is NavidromeQuery.Collection -> copy(page = page)
    is NavidromeQuery.Search -> copy(page = page)
    is NavidromeQuery.Browse -> copy(page = page)
}
internal fun NavidromeCollection.label(): Int = when (this) {
    NavidromeCollection.Favorites -> R.string.nav_favorites
    NavidromeCollection.Artists -> R.string.nav_artists
    NavidromeCollection.Bookmarks -> R.string.nav_bookmarks
    NavidromeCollection.Radios -> R.string.nav_radios
    NavidromeCollection.Queue -> R.string.nav_queue
}
internal fun NavidromeKind.label(): Int = when (this) {
    NavidromeKind.Track -> R.string.nav_track
    NavidromeKind.Album -> R.string.nav_album
    NavidromeKind.Artist -> R.string.nav_artist
    NavidromeKind.Radio -> R.string.nav_radio
}
