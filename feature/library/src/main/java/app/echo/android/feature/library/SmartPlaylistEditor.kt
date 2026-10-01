package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SmartPlaylistEditor(playlist: EchoPlaylist?, actions: LibraryExperienceActions, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(playlist?.name.orEmpty()) }
    var rule by remember { mutableStateOf(EchoSmartPlaylistRule()) }
    var loaded by remember { mutableStateOf(playlist == null) }
    var preview by remember { mutableStateOf<EchoSmartPlaylistPreview?>(null) }
    var previewBusy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(playlist?.id) {
        if (playlist != null) try { rule = actions.rule(playlist.id) ?: EchoSmartPlaylistRule(); loaded = true }
        catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
    }
    LaunchedEffect(rule, loaded) {
        if (!loaded || !rule.isValid) return@LaunchedEffect
        previewBusy = true
        delay(250)
        try { preview = actions.preview(rule); error = false }
        catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
        finally { previewBusy = false }
    }
    ModalBottomSheet(onDismissRequest = { if (!saving) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f).imePadding()) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text(stringResource(R.string.smart_editor_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.smart_editor_hint), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!loaded) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item { OutlinedTextField(name, { name = it.take(100) }, label = { Text(stringResource(R.string.smart_name)) },
                singleLine = true, enabled = loaded && !saving, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(rule.artist, { rule = rule.copy(artist = it.take(120)) },
                label = { Text(stringResource(R.string.smart_artist)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(rule.genre, { rule = rule.copy(genre = it.take(120)) },
                label = { Text(stringResource(R.string.smart_genre)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!rule.matchAny, { rule = rule.copy(matchAny = false) }, label = { Text(stringResource(R.string.smart_all)) })
                FilterChip(rule.matchAny, { rule = rule.copy(matchAny = true) }, label = { Text(stringResource(R.string.smart_any)) })
            } }
            item { OutlinedTextField(rule.album, { rule = rule.copy(album = it.take(120)) }, label = { Text(stringResource(R.string.smart_album)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(rule.folder, { rule = rule.copy(folder = it.take(120)) }, label = { Text(stringResource(R.string.smart_folder)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(rule.format, { rule = rule.copy(format = it.take(120)) }, label = { Text(stringResource(R.string.smart_format)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(rule.excludeText, { rule = rule.copy(excludeText = it.take(120)) }, label = { Text(stringResource(R.string.smart_exclude)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmartDurationField(rule.minimumDurationSeconds, R.string.smart_duration_from, Modifier.weight(1f)) { rule = rule.copy(minimumDurationSeconds = it) }
                SmartDurationField(rule.maximumDurationSeconds, R.string.smart_duration_to, Modifier.weight(1f)) { rule = rule.copy(maximumDurationSeconds = it) }
            } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.smart_favorites), Modifier.weight(1f).padding(top = 12.dp))
                Switch(rule.favoriteOnly, { rule = rule.copy(favoriteOnly = it) }, enabled = !saving)
            } }
            item {
                Text(stringResource(R.string.smart_not_played), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 7, 30, 90, 365).forEach { days ->
                        FilterChip(rule.notPlayedDays == days, { rule = rule.copy(notPlayedDays = days) },
                            label = { Text(if (days == 0) stringResource(R.string.smart_any_time) else androidx.compose.ui.res.pluralStringResource(R.plurals.smart_days, days, days)) })
                    }
                }
            }
            item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmartYearField(rule.minimumYear, R.string.smart_year_from, Modifier.weight(1f)) { rule = rule.copy(minimumYear = it) }
                SmartYearField(rule.maximumYear, R.string.smart_year_to, Modifier.weight(1f)) { rule = rule.copy(maximumYear = it) }
            } }
            item {
                Text(stringResource(R.string.smart_sort), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EchoSmartPlaylistSort.entries.forEach { sort -> FilterChip(rule.sort == sort, { rule = rule.copy(sort = sort) },
                        label = { Text(stringResource(when (sort) {
                            EchoSmartPlaylistSort.Title -> R.string.smart_sort_title
                            EchoSmartPlaylistSort.RecentlyAdded -> R.string.smart_sort_added
                            EchoSmartPlaylistSort.LeastPlayed -> R.string.smart_sort_least
                            EchoSmartPlaylistSort.MostPlayed -> R.string.smart_sort_most
                        })) }) }
                }
            }
            item {
                HorizontalDivider()
                if (previewBusy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.smart_preview_count, preview?.count ?: 0, preview?.count ?: 0),
                    Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleMedium)
                if (preview?.count == 0 && !previewBusy) Text(stringResource(R.string.smart_preview_empty),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(preview?.tracks.orEmpty(), key = { it.id }) { track -> Column {
                Text(track.title, maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                Text(track.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
        }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                if (error) Text(stringResource(R.string.library_tool_error), color = MaterialTheme.colorScheme.error)
                if (!rule.isValid) Text(stringResource(R.string.smart_year_error), color = MaterialTheme.colorScheme.error)
                Button(onClick = { saving = true; scope.launch {
                    try { actions.save(playlist?.id, name.trim(), rule); onDismiss() }
                    catch (e: CancellationException) { throw e } catch (_: Exception) { error = true }
                    finally { saving = false }
                } }, enabled = loaded && rule.isValid && name.isNotBlank() && !saving,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text(stringResource(if (saving) R.string.library_tool_saving else R.string.smart_save))
                }
            }
        }
    }
}

@Composable
private fun SmartYearField(value: Int, label: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    OutlinedTextField(if (value == 0) "" else value.toString(), { text ->
        if (text.isEmpty()) onChange(0) else text.takeIf { it.length <= 4 && it.all(Char::isDigit) }?.toIntOrNull()?.let(onChange)
    }, modifier, label = { Text(stringResource(label)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable
private fun SmartDurationField(value: Int, label: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    OutlinedTextField(if (value == 0) "" else value.toString(), { text ->
        if (text.isEmpty()) onChange(0) else text.toIntOrNull()?.takeIf { it in 1..86400 }?.let(onChange)
    }, modifier, label = { Text(stringResource(label)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}
