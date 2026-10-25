package app.echo.android.feature.radio

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.radio.*
import kotlinx.coroutines.launch

@Composable
fun RadioScreen(
    saved: List<EchoRadioStation>, visibleSaved: List<EchoRadioStation>, recent: List<EchoRadioStation>,
    directory: RadioDiscoveryState, failed: Boolean, playbackPending: Boolean, actions: RadioActions,
    currentId: String?, playing: Boolean, onBack: () -> Unit,
    streamTitle: String? = null, videoSurface: @Composable (String) -> Unit = {},
    interactive: Boolean = true,
) {
    var global by rememberSaveable { mutableStateOf(false) }
    var group by rememberSaveable { mutableStateOf("home") }
    var savedQuery by rememberSaveable { mutableStateOf("") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var filters by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var addOptions by remember { mutableStateOf(false) }
    var tools by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EchoRadioStation?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<EchoRadioStation?>(null) }
    var deletion by remember { mutableStateOf<EchoRadioStation?>(null) }
    var clearAll by remember { mutableStateOf(false) }
    var operationFailed by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    fun operation(block: suspend () -> Unit) { scope.launch {
        try { block(); operationFailed = false }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { operationFailed = true }
    } }
    fun browse(query: RadioDirectoryQuery = RadioDirectoryQuery(), view: String = "results") {
        global = true; group = view; searchOpen = false; actions.browse(query)
    }
    fun goBack() {
        if (searchOpen) { searchOpen = false; return }
        if (selected != null) { selected = null; return }
        if (!global || group == "home") { onBack(); return }
        val parent = RadioNavigation.parent(group, directory.query.tag)
        group = parent
        if (parent == "home") actions.browse(RadioDirectoryQuery()) else actions.closeDirectory()
    }
    LaunchedEffect(Unit) { if (global) actions.browse(directory.query) }
    DisposableEffect(Unit) { onDispose(actions.closeDirectory) }
    LaunchedEffect(savedQuery, favorites) { actions.searchSaved(savedQuery, favorites) }
    LaunchedEffect(global, group, directory.query) { listState.scrollToItem(0) }
    val detail = selected?.let { snapshot -> saved.firstOrNull { it.id == snapshot.id || it.url == snapshot.url } ?: snapshot }
    androidx.activity.compose.BackHandler(enabled = interactive) { goBack() }
    if (detail != null) {
        RadioStationDetail(detail, saved.any { it.id == detail.id || it.url == detail.url }, currentId == "radio:${detail.id}" && playing,
            playbackPending && currentId == "radio:${detail.id}", actions, onBack = { selected = null }, onEdit = { editing = detail; editorOpen = true }, videoSurface = videoSurface,
            onFilter = { query -> selected = null; browse(query) }, streamTitle = streamTitle?.takeIf { currentId == "radio:${detail.id}" })
    } else BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 720.dp) 3 else 2
        val directoryRows = remember(directory.rows, columns) { directory.rows.chunked(columns) }
        val savedRows = remember(visibleSaved, columns) { visibleSaved.chunked(columns) }
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { goBack() }) { EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.radio_back)) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.radio_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (global && group in setOf("music", "spoken")) Text(stringResource(if (global) when (group) { "music" -> R.string.radio_category_music; "spoken" -> R.string.radio_category_spoken; else -> R.string.radio_global } else R.string.radio_saved),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { searchOpen = !searchOpen }) { EchoIcon(Icons.Rounded.Search, stringResource(R.string.radio_search)) }
                if (!global) IconButton(onClick = { addOptions = true }) { EchoIcon(Icons.Rounded.Add, stringResource(R.string.radio_add)) }
                if (global) IconButton(onClick = { filters = true }) { EchoIcon(Icons.Rounded.Tune, stringResource(R.string.radio_filters)) }
                else Box {
                    IconButton(onClick = { tools = true }) { EchoIcon(Icons.Rounded.MoreHoriz, stringResource(R.string.radio_more)) }
                    DropdownMenu(tools, onDismissRequest = { tools = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.radio_import)) }, onClick = { tools = false; actions.importFile() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.radio_export)) }, enabled = saved.isNotEmpty(), onClick = { tools = false; actions.exportFile() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.radio_export_m3u)) }, enabled = saved.isNotEmpty(), onClick = { tools = false; actions.exportPlaylist() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.radio_information)) }, onClick = { tools = false; info = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.radio_clear)) }, enabled = saved.isNotEmpty(), onClick = { tools = false; clearAll = true })
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!global, { global = false; actions.closeDirectory(); searchOpen = false }, border = null, label = { Text(stringResource(R.string.radio_saved)) })
                FilterChip(global, { browse(view = "home") }, border = null, label = { Text(stringResource(R.string.radio_global)) })
                if (!global) FilterChip(favorites, { favorites = !favorites }, border = null, label = { Text(stringResource(R.string.radio_favorites)) })
                if (global) IconButton(onClick = { info = true }) { EchoIcon(Icons.Rounded.Info, stringResource(R.string.radio_information)) }
            }
            if (searchOpen) {
                if (global) RadioDirectorySearch(directory) { browse(it) }
                else OutlinedTextField(savedQuery, { savedQuery = it.take(128) }, Modifier.fillMaxWidth().padding(bottom = 12.dp), singleLine = true,
                    label = { Text(stringResource(R.string.radio_search_saved)) })
            }
            if (failed || operationFailed) RadioError(onRetry = { actions.retrySaved(); operationFailed = false })
            if (global && directory.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (global && group == "home") {
                    if (recent.isNotEmpty()) item("recent") {
                        RadioSectionHeading(stringResource(R.string.radio_recent))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                            items(recent, key = { it.id }) { station -> RadioStationCard(station, currentId == "radio:${station.id}" && playing, playbackPending && currentId == "radio:${station.id}", actions,
                                onOpen = { selected = station }, modifier = Modifier.width(180.dp)) }
                        }
                    }
                    item("categories") { RadioCategories("home", onGroup = { value ->
                        when (value) {
                            "locations" -> filters = true
                            "sports" -> browse(directory.query.copy(name = "", tag = "sports", page = 0))
                            else -> { group = value; actions.closeDirectory() }
                        }
                    }, onTag = {}) }
                    item("popular") { RadioSectionHeading(stringResource(R.string.radio_popular), onMore = { browse() }) }
                }
                if (global && group in listOf("music", "spoken")) item("genres") {
                    RadioCategories(group, onGroup = {}, onTag = { browse(directory.query.copy(name = "", tag = it, page = 0)) })
                }
                if (global && group in listOf("home", "results")) {
                    if (group == "results") item("result-heading") {
                        RadioSectionHeading(listOf(directory.query.tag, directory.query.countryCode, directory.query.language, directory.query.name)
                            .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { stringResource(R.string.radio_popular) })
                    }
                    if (directory.failed) item("error") { RadioError { actions.browse(directory.query) } }
                    if (!directory.loading && !directory.failed && directory.rows.isEmpty()) item("empty") { RadioNoResults { browse(view = "home") } }
                    items(directoryRows, key = { "directory:${it.first().id}" }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { hit ->
                                val original = hit.toStation()
                                val existing = saved.firstOrNull { it.id == original.id || it.url == original.url }
                                val station = existing?.copy(artworkUrl = existing.artworkUrl ?: original.artworkUrl) ?: original
                                RadioStationCard(station, currentId == "radio:${station.id}" && playing, playbackPending && currentId == "radio:${station.id}", actions,
                                    onOpen = { selected = station }, modifier = Modifier.weight(1f), saved = existing != null,
                                    onSave = { operation { actions.save(station) } })
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    item("pages") { RadioPagination(directory, actions) }
                }
                if (!global) {
                    if (visibleSaved.isEmpty()) item("empty-saved") {
                        if (savedQuery.isNotBlank() || favorites) RadioNoResults { savedQuery = ""; favorites = false }
                        else RadioEmptyState { addOptions = true }
                    }
                    items(savedRows, key = { "saved:${it.first().id}" }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { station -> RadioStationCard(station, currentId == "radio:${station.id}" && playing, playbackPending && currentId == "radio:${station.id}", actions,
                                onOpen = { selected = station }, modifier = Modifier.weight(1f),
                                onFavorite = { operation { actions.favorite(station.id) } }, onEdit = { editing = station; editorOpen = true }, onDelete = { deletion = station }) }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
    if (addOptions) RadioAddOptions(onDismiss = { addOptions = false }, onExplore = { addOptions = false; browse(view = "home") },
        onManual = { addOptions = false; editing = null; editorOpen = true }, onClipboard = {
            addOptions = false; actions.playClipboard(clipboard.getText()?.text.orEmpty())
        })
    if (filters) RadioFilterDialog(directory.query, onApply = { browse(it); filters = false }, onDismiss = { filters = false })
    if (info) AlertDialog(onDismissRequest = { info = false }, title = { Text(stringResource(R.string.radio_information)) },
        text = { Text(stringResource(R.string.radio_notice)) }, confirmButton = { TextButton(onClick = { info = false }) { Text(stringResource(R.string.radio_close)) } })
    if (editorOpen) RadioStationEditor(editing, actions, onDismiss = { editorOpen = false })
    if (deletion != null || clearAll) AlertDialog(onDismissRequest = { deletion = null; clearAll = false },
        title = { Text(stringResource(R.string.radio_delete)) }, text = { Text(stringResource(R.string.radio_delete_notice)) },
        confirmButton = { TextButton(onClick = { val id = deletion?.id; operation { if (id != null) actions.delete(id) else actions.clear() }; deletion = null; clearAll = false }) { Text(stringResource(R.string.radio_delete)) } },
        dismissButton = { TextButton(onClick = { deletion = null; clearAll = false }) { Text(stringResource(R.string.radio_cancel)) } })
}
