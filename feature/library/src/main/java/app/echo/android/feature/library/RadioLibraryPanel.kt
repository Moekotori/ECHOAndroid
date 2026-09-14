package app.echo.android.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.model.radio.EchoRadioStation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun RadioLibraryPanel(
    stations: List<EchoRadioStation>,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    onPlay: (EchoRadioStation) -> Unit,
    onSave: suspend (String?, String, String) -> Unit,
    onDelete: suspend (String) -> Unit,
) {
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EchoRadioStation?>(null) }
    var deleting by remember { mutableStateOf<EchoRadioStation?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.radio_hint), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { editing = null; editorOpen = true }, enabled = !loadFailed) {
                Icon(Icons.Rounded.Add, null)
                Text(stringResource(R.string.radio_add))
            }
        }
        if (loadFailed) {
            Text(stringResource(R.string.radio_load_error))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.radio_retry)) }
        } else if (stations.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.radio_empty), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(stations, key = { it.id }) { station ->
                    ListItem(
                        modifier = Modifier.clickable { onPlay(station) },
                        leadingContent = { Icon(Icons.Rounded.Radio, stringResource(R.string.radio_play)) },
                        headlineContent = { Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(station.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { editing = station; editorOpen = true }) {
                                    Icon(Icons.Rounded.Edit, stringResource(R.string.radio_edit))
                                }
                                IconButton(onClick = { deleting = station }) {
                                    Icon(Icons.Rounded.Delete, stringResource(R.string.radio_delete))
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
            }
        }
    }
    if (editorOpen) RadioStationEditor(editing, onSave, onDismiss = { editorOpen = false })
    deleting?.let { station -> RadioDeleteDialog(station, onDelete, onDismiss = { deleting = null }) }
}

@Composable
private fun RadioStationEditor(
    station: EchoRadioStation?,
    onSave: suspend (String?, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(station?.id) { mutableStateOf(station?.name.orEmpty()) }
    var url by rememberSaveable(station?.id) { mutableStateOf(station?.url.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val validUrl = remember(url) { EchoRadioStation.validUrl(url) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(if (station == null) R.string.radio_add else R.string.radio_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(120); failed = false },
                    label = { Text(stringResource(R.string.radio_name)) }, singleLine = true, enabled = !busy,
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it.take(4096); failed = false },
                    label = { Text(stringResource(R.string.radio_url)) }, singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    isError = url.isNotBlank() && !validUrl,
                    supportingText = { Text(stringResource(R.string.radio_url_hint)) },
                )
                if (failed) Text(stringResource(R.string.radio_save_error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && name.isNotBlank() && validUrl, onClick = {
                busy = true
                scope.launch {
                    try {
                        onSave(station?.id, name, url)
                        onDismiss()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        failed = true
                    } finally { busy = false }
                }
            }) { Text(stringResource(R.string.radio_save)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.radio_cancel)) } },
    )
}

@Composable
private fun RadioDeleteDialog(station: EchoRadioStation, onDelete: suspend (String) -> Unit, onDismiss: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.radio_delete)) },
        text = { Text(if (failed) stringResource(R.string.radio_write_error) else stringResource(R.string.radio_delete_prompt, station.name)) },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try { onDelete(station.id); onDismiss() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.radio_delete)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.radio_cancel)) } },
    )
}
