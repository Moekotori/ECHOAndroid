package app.echo.android.feature.radio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.radio.*
import kotlinx.coroutines.launch

@Composable
fun RadioScreen(
    saved: List<EchoRadioStation>, visibleSaved: List<EchoRadioStation>, recent: List<EchoRadioStation>,
    directory: RadioDiscoveryState, failed: Boolean, playbackPending: Boolean, actions: RadioActions,
    currentId: String?, playing: Boolean,
    streamTitle: String? = null, videoSurface: @Composable (String) -> Unit = {},
    interactive: Boolean = true,
    placeToolbarActions: @Composable (@Composable () -> Unit) -> Unit,
) {
    var global by rememberSaveable { mutableStateOf(false) }
    var group by rememberSaveable { mutableStateOf("home") }
    var savedQuery by rememberSaveable { mutableStateOf("") }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var filters by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var addOptions by remember { mutableStateOf(false) }
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
        if (!global || group == "home") return
        val parent = RadioNavigation.parent(group, directory.query.tag)
        group = parent
        if (parent == "home") actions.browse(RadioDirectoryQuery()) else actions.closeDirectory()
    }
    LaunchedEffect(interactive) {
        if (interactive && global && (group == "home" || group == "results")) actions.browse(directory.query)
    }
    DisposableEffect(interactive) {
        onDispose { if (interactive) actions.closeDirectory() }
    }
    LaunchedEffect(interactive, savedQuery, favorites) {
        if (interactive) actions.searchSaved(savedQuery, favorites)
    }
    LaunchedEffect(global, group, directory.query) { listState.scrollToItem(0) }
    val detail = selected?.let { snapshot -> saved.firstOrNull { it.id == snapshot.id || it.url == snapshot.url } ?: snapshot }
    val canNavigateUp = global && group != "home"
    if (detail == null) {
        placeToolbarActions {
            IconButton(onClick = { searchOpen = !searchOpen }) { EchoIcon(Icons.Rounded.Search, stringResource(R.string.radio_search)) }
            if (global) {
                IconButton(onClick = { filters = true }) { EchoIcon(Icons.Rounded.Tune, stringResource(R.string.radio_filters)) }
            } else {
                IconButton(onClick = { addOptions = true }) { EchoIcon(Icons.Rounded.Add, stringResource(R.string.radio_add)) }
                Box {
                    var tools by remember { mutableStateOf(false) }
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
        }
    }
    app.echo.android.design.EchoPageBackHandler(enabled = interactive && (searchOpen || selected != null || canNavigateUp)) { goBack() }
    app.echo.android.design.EchoPageContent(
        targetState = detail,
        contentKey = { it?.id ?: "radio-list" },
        isBackward = { _, target -> target == null },
        label = "radio-detail",
    ) { shownDetail ->
    if (shownDetail != null) {
        RadioStationDetail(shownDetail, saved.any { it.id == shownDetail.id || it.url == shownDetail.url }, currentId == "radio:${shownDetail.id}" && playing,
            playbackPending && currentId == "radio:${shownDetail.id}", actions, onBack = { selected = null }, onEdit = { editing = shownDetail; editorOpen = true }, videoSurface = videoSurface,
            onFilter = { query -> selected = null; browse(query) }, streamTitle = streamTitle?.takeIf { currentId == "radio:${shownDetail.id}" })
    } else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            if (canNavigateUp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { goBack() }) {
                        EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.radio_back))
                    }
                    if (group == "music" || group == "spoken") {
                        Text(
                            stringResource(if (group == "music") R.string.radio_category_music else R.string.radio_category_spoken),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            RadioTabs(global, favorites,
                onSaved = { global = false; actions.closeDirectory(); searchOpen = false },
                onGlobal = { browse(view = "home") },
                onFavorites = { favorites = it },
                onInfo = { info = true },
            )
            if (searchOpen) {
                if (global) RadioDirectorySearch(directory) { browse(it) }
                else OutlinedTextField(savedQuery, { savedQuery = it.take(128) }, Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), singleLine = true,
                    label = { Text(stringResource(R.string.radio_search_saved)) })
            }
            if (failed || operationFailed) RadioError(onRetry = { actions.retrySaved(); operationFailed = false })
            if (global && directory.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(top = 6.dp, bottom = 32.dp)) {
                if (global && group == "home") {
                    item("categories") {
                        RadioCategoryDial { value ->
                            when (value) {
                                "locations" -> filters = true
                                "sports" -> browse(directory.query.copy(name = "", tag = "sports", page = 0))
                                else -> { group = value; actions.closeDirectory() }
                            }
                        }
                    }
                    if (recent.isNotEmpty()) item("recent") {
                        RadioSectionHeading(stringResource(R.string.radio_recent))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            items(recent, key = { it.id }) { station ->
                                RadioStationRow(station, currentId == "radio:${station.id}" && playing,
                                    playbackPending && currentId == "radio:${station.id}", actions,
                                    onOpen = { selected = station }, modifier = Modifier.width(248.dp))
                            }
                        }
                    }
                    item("popular") { RadioSectionHeading(stringResource(R.string.radio_popular), onMore = { browse() }) }
                }
                if (global && group in listOf("music", "spoken")) item("genres") {
                    RadioGenres(group) { browse(directory.query.copy(name = "", tag = it, page = 0)) }
                }
                if (global && group in listOf("home", "results")) {
                    if (group == "results") item("result-heading") {
                        RadioSectionHeading(listOf(directory.query.tag, directory.query.countryCode, directory.query.language, directory.query.name)
                            .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { stringResource(R.string.radio_popular) })
                    }
                    if (directory.failed) item("error") { RadioError { actions.browse(directory.query) } }
                    if (!directory.loading && !directory.failed && directory.rows.isEmpty()) item("empty") { RadioNoResults { browse(view = "home") } }
                    items(directory.rows, key = { "directory:${it.url}" }, contentType = { "directory-station" }) { hit ->
                        val (station, isSaved) = remember(hit, saved) {
                            val original = hit.toStation()
                            val existing = saved.firstOrNull { it.id == original.id || it.url == original.url }
                            val resolved = existing?.copy(artworkUrl = existing.artworkUrl ?: original.artworkUrl) ?: original
                            resolved to (existing != null)
                        }
                        RadioStationRow(station, currentId == "radio:${station.id}" && playing,
                            playbackPending && currentId == "radio:${station.id}", actions,
                            onOpen = { selected = station }, modifier = Modifier.fillMaxWidth(), saved = isSaved,
                            onSave = { operation { actions.save(station) } })
                        RadioStationDivider()
                    }
                    item("pages") { RadioPagination(directory, actions) }
                }
                if (!global) {
                    if (visibleSaved.isEmpty()) item("empty-saved") {
                        if (savedQuery.isNotBlank() || favorites) RadioNoResults { savedQuery = ""; favorites = false }
                        else RadioEmptyState { addOptions = true }
                    }
                    items(visibleSaved, key = { "saved:${it.id}" }, contentType = { "saved-station" }) { station ->
                        RadioStationRow(station, currentId == "radio:${station.id}" && playing,
                            playbackPending && currentId == "radio:${station.id}", actions,
                            onOpen = { selected = station }, modifier = Modifier.fillMaxWidth(),
                            onFavorite = { operation { actions.favorite(station.id) } },
                            onEdit = { editing = station; editorOpen = true }, onDelete = { deletion = station })
                        RadioStationDivider()
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
