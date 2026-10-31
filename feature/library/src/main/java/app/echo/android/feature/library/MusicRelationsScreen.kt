package app.echo.android.feature.library

import app.echo.android.design.EchoPageBackHandler as BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoDropdownMenu
import app.echo.android.design.EchoDropdownMenuItem
import app.echo.android.model.library.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RelationSelectionSaver = listSaver<List<EchoMusicRelation>, String>(
    save = { list -> list.flatMap { listOf(it.kind.name, it.key, it.title, it.subtitle) } },
    restore = { values -> values.chunked(4).map { EchoMusicRelation(EchoMusicRelationKind.valueOf(it[0]), it[1], it[2], it[3]) } },
)

@Composable
internal fun EchoMusicRelationKind.label(): String = stringResource(when (this) {
    EchoMusicRelationKind.Composer -> R.string.music_relation_composers
    EchoMusicRelationKind.Work -> R.string.music_relation_works
    EchoMusicRelationKind.Movement -> R.string.music_relation_movements
    EchoMusicRelationKind.Conductor -> R.string.music_relation_conductors
    EchoMusicRelationKind.Performer -> R.string.music_relation_performers
    EchoMusicRelationKind.Recording -> R.string.music_relation_recordings
    EchoMusicRelationKind.Contributor -> R.string.music_relation_contributors
    EchoMusicRelationKind.Label -> R.string.music_relation_labels
    EchoMusicRelationKind.CreditRole -> R.string.music_relation_credit_roles
})

@Composable
fun MusicRelationsScreen(actions: MusicRelationsActions, onBack: () -> Unit, onPlay: (EchoTrack) -> Unit,
    modifier: Modifier = Modifier, playback: MusicRelationsPlaybackActions? = null) {
    var selection by rememberSaveable(stateSaver = RelationSelectionSaver) { mutableStateOf(emptyList<EchoMusicRelation>()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var debounced by remember { mutableStateOf(query) }
    var ready by remember(actions) { mutableStateOf(false) }
    var initAttempt by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    var failedRead by remember { mutableStateOf(false) }
    var readJob by remember { mutableStateOf<Job?>(null) }
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var categoryMenu by remember { mutableStateOf(false) }
    var pathMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val reading = readJob?.isActive == true
    val overviewFlow = remember(actions, selection) { actions.overview(selection) }
    val overview by overviewFlow.collectAsState(initial = EchoMusicRelationOverview())
    fun readTags() {
        failed = false
        failedRead = false
        progress = 0 to 0
        readJob = scope.launch {
            try { actions.readTags { done, errors -> scope.launch { progress = done to errors } } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true; failedRead = true }
            finally { readJob = null }
        }
    }
    fun back() {
        if (selection.isEmpty()) onBack() else { selection = selection.dropLast(1); query = "" }
    }
    BackHandler { back() }
    LaunchedEffect(query) { delay(250); debounced = query.trim() }
    LaunchedEffect(actions, initAttempt) {
        failed = false
        failedRead = false
        try { actions.initialize(); ready = true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }
    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = ::back) {
                EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.music_relation_back), tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(stringResource(R.string.music_relations_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(enabled = ready && !reading, onClick = ::readTags) {
                EchoIcon(Icons.Rounded.Refresh, stringResource(R.string.music_relation_read_tags), tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Text(stringResource(R.string.music_relation_tag_hint), Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (ready) Text(stringResource(R.string.music_relation_overview, overview.taggedTrackCount, overview.trackCount,
            overview.counts.values.sum()), Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (selection.isNotEmpty()) Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TextButton(onClick = { pathMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selection.joinToString(" › ") { it.title }, Modifier.weight(1f), maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                EchoIcon(Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            EchoDropdownMenu(expanded = pathMenu, onDismissRequest = { pathMenu = false }) {
                EchoDropdownMenuItem(selected = false, text = { Text(stringResource(R.string.music_relation_all)) },
                    onClick = { selection = emptyList(); query = ""; pathMenu = false })
                selection.forEachIndexed { index, relation ->
                    EchoDropdownMenuItem(selected = index == selection.lastIndex, text = { Text(relation.title, maxLines = 2) },
                        onClick = { selection = selection.take(index + 1); query = ""; pathMenu = false })
                }
            }
        }
        if (reading) LinearProgressIndicator(Modifier.fillMaxWidth())
        progress?.let { (done, errors) ->
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.music_relation_progress, done, errors), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                if (reading) TextButton(onClick = { readJob?.cancel() }) { Text(stringResource(R.string.music_relation_cancel)) }
            }
        }
        if (failed) Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.music_relation_error), Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { if (failedRead) readTags() else initAttempt++ }) { Text(stringResource(R.string.music_relation_retry)) }
        }
        OutlinedTextField(value = query, onValueChange = { query = it.take(200) }, singleLine = true,
            label = { Text(stringResource(R.string.music_relation_search)) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TextButton(onClick = { categoryMenu = true }) {
                Text(EchoMusicRelationKind.entries.getOrNull(tab)?.label() ?: stringResource(R.string.music_relation_tracks),
                    color = MaterialTheme.colorScheme.onSurface)
                EchoIcon(Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            EchoDropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                val options = EchoMusicRelationKind.entries
                for (index in -1 until options.size) {
                    EchoDropdownMenuItem(selected = tab == index,
                        text = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Text(if (index < 0) stringResource(R.string.music_relation_tracks) else options[index].label(), Modifier.weight(1f))
                            Text((if (index < 0) overview.trackCount else overview.counts[options[index]] ?: 0).toString(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } },
                        onClick = { tab = index; query = ""; categoryMenu = false })
                }
            }
        }
        if (!ready && !failed) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        if (ready) key(selection, tab, debounced) {
            MusicRelationsResults(actions, selection, EchoMusicRelationKind.entries.getOrNull(tab), debounced,
                onSelect = { relation -> selection = selection.filter { it.kind != relation.kind } + relation; tab = -1; query = "" },
                onPlay = onPlay, modifier = Modifier.weight(1f), playback = playback)
        }
    }
}
