package app.echo.android.feature.radio

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoIcon
import app.echo.android.model.radio.*
import kotlinx.coroutines.launch

data class RadioDiscoveryState(
    val query: RadioDirectoryQuery = RadioDirectoryQuery(), val rows: List<EchoRadioDirectoryStation> = emptyList(),
    val hasNext: Boolean = false, val loading: Boolean = false, val failed: Boolean = false,
)

class RadioActions(
    val browse: (RadioDirectoryQuery) -> Unit, val closeDirectory: () -> Unit,
    val play: (EchoRadioStation, String?) -> Unit, val stop: () -> Unit,
    val save: suspend (EchoRadioStation) -> Unit, val delete: suspend (String) -> Unit,
    val favorite: suspend (String) -> Unit, val clear: suspend () -> Unit,
    val importStations: suspend (List<EchoRadioStation>) -> Unit,
    val probe: suspend (String) -> RadioProbeResult,
    val searchSaved: (String, Boolean) -> Unit, val importFile: () -> Unit, val exportFile: () -> Unit, val exportPlaylist: () -> Unit,
    val retrySaved: () -> Unit, val openUrl: (String) -> Unit,
)

@Composable
fun RadioScreen(
    saved: List<EchoRadioStation>, visibleSaved: List<EchoRadioStation>, recent: List<EchoRadioStation>,
    directory: RadioDiscoveryState, failed: Boolean, playbackPending: Boolean, actions: RadioActions,
    currentId: String?, playing: Boolean, onBack: () -> Unit,
    streamTitle: String? = null,
    videoSurface: @Composable (String) -> Unit = {},
) {
    var global by rememberSaveable { mutableStateOf(false) }
    var group by rememberSaveable { mutableStateOf("home") }
    var savedQuery by rememberSaveable { mutableStateOf("") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var filters by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EchoRadioStation?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<EchoRadioStation?>(null) }
    var deletion by remember { mutableStateOf<EchoRadioStation?>(null) }
    var clearAll by remember { mutableStateOf(false) }
    var operationFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun operation(block: suspend () -> Unit) { scope.launch {
        try { block(); operationFailed = false }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { operationFailed = true }
    } }
    LaunchedEffect(global) { if (global) actions.browse(directory.query) else actions.closeDirectory() }
    DisposableEffect(Unit) { onDispose(actions.closeDirectory) }
    LaunchedEffect(savedQuery, favorites) { actions.searchSaved(savedQuery, favorites) }
    val detail = selected?.let { snapshot -> saved.firstOrNull { it.id == snapshot.id || it.url == snapshot.url } ?: snapshot }
    androidx.activity.compose.BackHandler {
        if (selected != null) selected = null
        else if (global && group != "home") { group = "home"; actions.browse(RadioDirectoryQuery()) }
        else onBack()
    }
    if (detail != null) {
        RadioStationDetail(detail, saved.any { it.id == detail.id || it.url == detail.url }, currentId == "radio:${detail.id}" && playing,
            playbackPending, actions, onBack = { selected = null }, onEdit = { editing = detail; editorOpen = true }, videoSurface = videoSurface,
            onFilter = { query -> selected = null; global = true; group = "results"; actions.browse(query) },
            streamTitle = streamTitle?.takeIf { currentId == "radio:${detail.id}" })
    } else Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (global && group != "home") { group = "home"; actions.browse(RadioDirectoryQuery()) } else onBack()
            }) { EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.radio_back)) }
            Text(stringResource(R.string.radio_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = { info = true }) { EchoIcon(Icons.Rounded.Info, stringResource(R.string.radio_information)) }
            if (global) IconButton(onClick = { filters = true }) { EchoIcon(Icons.Rounded.Tune, stringResource(R.string.radio_filters)) }
            else IconButton(onClick = { editing = null; editorOpen = true }) { EchoIcon(Icons.Rounded.Add, stringResource(R.string.radio_add)) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!global, { global = false }, label = { Text(stringResource(R.string.radio_saved)) })
            FilterChip(global, { if (global) actions.browse(RadioDirectoryQuery()); global = true; group = "home" }, label = { Text(stringResource(R.string.radio_global)) })
            if (!global) FilterChip(favorites, { favorites = !favorites }, label = { Text(stringResource(R.string.radio_favorites)) })
        }
        if (failed || operationFailed) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.radio_error), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { actions.retrySaved(); operationFailed = false }) { Text(stringResource(R.string.radio_retry)) }
        }
        if (!global) {
            OutlinedTextField(savedQuery, { savedQuery = it.take(128) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.radio_search_saved)) })
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = actions.importFile) { Text(stringResource(R.string.radio_import)) }
                TextButton(onClick = actions.exportFile) { Text(stringResource(R.string.radio_export)) }
                TextButton(onClick = actions.exportPlaylist) { Text(stringResource(R.string.radio_export_m3u)) }
                TextButton(onClick = { clearAll = true }, enabled = saved.isNotEmpty()) { Text(stringResource(R.string.radio_clear)) }
            }
        } else {
            RadioDirectorySearch(directory, onSearch = { group = "results"; actions.browse(it) })
            if (directory.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (global && group == "home") {
                if (recent.isNotEmpty()) {
                    item { Text(stringResource(R.string.radio_recent), style = MaterialTheme.typography.titleMedium) }
                    items(recent, key = { "recent:${it.id}" }) { station -> RadioStationRow(station, currentId, playing, playbackPending, actions,
                        onOpen = { selected = station }) }
                }
                item { RadioCategories("home", onGroup = { value ->
                    if (value == "locations") filters = true
                    else if (value == "sports") { group = "results"; actions.browse(directory.query.copy(tag = "sports", page = 0)) }
                    else { group = value; actions.closeDirectory() }
                }, onTag = {}) }
                item { TextButton(onClick = { actions.browse(RadioDirectoryQuery()) }) { Text(stringResource(R.string.radio_popular)) } }
            }
            if (global && group in listOf("music", "spoken")) item {
                RadioCategories(group, onGroup = {}, onTag = { tag -> group = "results"; actions.browse(directory.query.copy(tag = tag, page = 0)) })
            }
            if (global && group in listOf("home", "results")) {
                if (directory.failed) item {
                    Text(stringResource(R.string.radio_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { actions.browse(directory.query) }) { Text(stringResource(R.string.radio_retry)) }
                }
                if (!directory.loading && !directory.failed && directory.rows.isEmpty()) item { Text(stringResource(R.string.radio_empty)) }
                items(directory.rows, key = { "directory:${it.id}" }) { result ->
                    val station = result.toStation()
                    RadioStationRow(station, currentId, playing, playbackPending, actions, onOpen = { selected = station },
                        onSave = { operation { actions.save(saved.firstOrNull { it.id == station.id || it.url == station.url } ?: station) } })
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = directory.query.page > 0 && !directory.loading, onClick = { actions.browse(directory.query.copy(page = directory.query.page - 1)) }) { Text(stringResource(R.string.radio_previous)) }
                        Text(stringResource(R.string.radio_page, directory.query.page + 1))
                        TextButton(enabled = directory.hasNext && !directory.loading, onClick = { actions.browse(directory.query.copy(page = directory.query.page + 1)) }) { Text(stringResource(R.string.radio_next)) }
                    }
                }
            }
            if (!global) {
                if (visibleSaved.isEmpty()) item { Text(stringResource(R.string.radio_empty), Modifier.padding(vertical = 24.dp)) }
                items(visibleSaved, key = { "saved:${it.id}" }) { station ->
                    RadioStationRow(station, currentId, playing, playbackPending, actions, onOpen = { selected = station },
                        onFavorite = { operation { actions.favorite(station.id) } },
                        onEdit = { editing = station; editorOpen = true }, onDelete = { deletion = station })
                }
            }
        }
    }
    if (filters) RadioFilterDialog(directory.query, onApply = { group = "results"; actions.browse(it); filters = false }, onDismiss = { filters = false })
    if (info) AlertDialog(onDismissRequest = { info = false }, title = { Text(stringResource(R.string.radio_information)) },
        text = { Text(stringResource(R.string.radio_notice)) }, confirmButton = { TextButton(onClick = { info = false }) { Text(stringResource(R.string.radio_close)) } })
    if (editorOpen) RadioStationEditor(editing, actions, onDismiss = { editorOpen = false })
    if (deletion != null || clearAll) AlertDialog(onDismissRequest = { deletion = null; clearAll = false },
        title = { Text(stringResource(R.string.radio_delete)) }, text = { Text(stringResource(R.string.radio_delete_notice)) },
        confirmButton = { TextButton(onClick = { val id = deletion?.id; operation { if (id != null) actions.delete(id) else actions.clear() }; deletion = null; clearAll = false }) { Text(stringResource(R.string.radio_delete)) } },
        dismissButton = { TextButton(onClick = { deletion = null; clearAll = false }) { Text(stringResource(R.string.radio_cancel)) } })
}

@Composable
private fun RadioStationRow(station: EchoRadioStation, currentId: String?, playing: Boolean, pending: Boolean, actions: RadioActions,
    onOpen: () -> Unit, onSave: (() -> Unit)? = null, onFavorite: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null, onDelete: (() -> Unit)? = null) {
    var more by remember { mutableStateOf(false) }
    ListItem(modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = { EchoArtworkImage(station.artworkUrl, null, Modifier.size(48.dp)) },
        headlineContent = { Text(station.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(listOfNotNull(station.countryCode.takeIf { it.isNotBlank() }, station.codec,
            station.bitrateKbps?.let { "$it kbps" }, station.genre.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { station.url }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row {
                IconButton(enabled = !pending, onClick = { if (currentId == "radio:${station.id}" && playing) actions.stop() else actions.play(station, null) }) {
                    EchoIcon(if (currentId == "radio:${station.id}" && playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, stringResource(R.string.radio_play_stop))
                }
                if (onSave != null) IconButton(onClick = onSave) { EchoIcon(Icons.Rounded.Add, stringResource(R.string.radio_save)) }
                if (onFavorite != null) Box {
                    IconButton(onClick = { more = true }) { EchoIcon(Icons.Rounded.MoreVert, stringResource(R.string.radio_more)) }
                    DropdownMenu(more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(text = { Text(stringResource(if (station.favorite) R.string.radio_unfavorite else R.string.radio_favorite)) }, onClick = { more = false; onFavorite() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.radio_edit)) }, onClick = { more = false; onEdit?.invoke() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.radio_delete)) }, onClick = { more = false; onDelete?.invoke() })
                    }
                }
            }
        }, colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
}
