package app.echo.android.feature.radio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoArtworkImage
import app.echo.android.design.EchoIcon
import app.echo.android.model.radio.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun RadioStationDetail(station: EchoRadioStation, saved: Boolean, playing: Boolean, pending: Boolean,
    actions: RadioActions, onBack: () -> Unit, onEdit: () -> Unit, videoSurface: @Composable (String) -> Unit,
    onFilter: (RadioDirectoryQuery) -> Unit, streamTitle: String?) {
    var expanded by androidx.compose.runtime.saveable.rememberSaveable(station.id) { mutableStateOf(false) }
    var overflow by remember(station.id, station.description) { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val streams = remember(station) { listOf(EchoRadioStream(station.url, station.codec, station.bitrateKbps)) + station.streams }
    fun save(value: EchoRadioStation) { scope.launch {
        try { actions.save(value); failed = false }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
    } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.radio_back)) }
                Text(stringResource(R.string.radio_station_details), style = MaterialTheme.typography.titleMedium)
            }
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        RadioArtwork(station, Modifier.size(80.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.radio_network_stream), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(station.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(listOf(station.countryCode, station.language).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    streamTitle?.takeIf { it != station.name && it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.titleSmall)
                    }
                    if (station.description.isNotBlank()) {
                        Text(station.description, style = MaterialTheme.typography.bodyMedium, maxLines = if (expanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis, onTextLayout = { if (!expanded) overflow = it.hasVisualOverflow },
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (overflow || expanded) TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                            Text(stringResource(if (expanded) R.string.radio_collapse_description else R.string.radio_expand_description))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(enabled = !pending, onClick = { if (playing) actions.stop() else actions.play(station, null) }, modifier = Modifier.weight(1f)) {
                            EchoIcon(if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp)); Text(stringResource(if (playing) R.string.radio_stop else R.string.radio_play))
                        }
                        if (saved) IconButton(onClick = onEdit) { EchoIcon(Icons.Rounded.Edit, stringResource(R.string.radio_edit)) }
                        else IconButton(onClick = { save(station) }) { EchoIcon(Icons.Rounded.Add, stringResource(R.string.radio_save)) }
                        station.homepage?.let { url -> IconButton(onClick = { actions.openUrl(url) }) { EchoIcon(Icons.Rounded.OpenInNew, stringResource(R.string.radio_website)) } }
                    }
                    if (playing) TextButton(onClick = actions.openPlayer) { Text(stringResource(R.string.radio_open_player)) }
                }
            }
            if (failed) RadioError { save(station) }
            if (playing) station.videoUrl?.let { videoSurface(it) }
            RadioDetailTags(station, onFilter)
            Text(stringResource(R.string.radio_streams), Modifier.padding(top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        items(streams, key = { it.url }) { stream ->
            ListItem(modifier = Modifier.padding(vertical = 4.dp), headlineContent = { Text(listOfNotNull(stream.codec, stream.bitrateKbps?.let { "$it kbps" }).joinToString(" · ").ifBlank { stringResource(R.string.radio_network_stream) }) },
                supportingContent = { Text(stream.url, maxLines = 2, overflow = TextOverflow.Ellipsis) }, leadingContent = {
                    IconButton(enabled = !pending, onClick = { actions.play(station, stream.url) }) { EchoIcon(Icons.Rounded.PlayArrow, stringResource(R.string.radio_play)) }
                }, trailingContent = {
                    if (stream.url == (station.defaultStreamUrl ?: station.url)) EchoIcon(Icons.Rounded.CheckCircle, stringResource(R.string.radio_default), Modifier.size(20.dp))
                    else if (saved) TextButton(onClick = { save(station.copy(defaultStreamUrl = stream.url)) }) { Text(stringResource(R.string.radio_make_default)) }
                }, colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RadioDetailTags(station: EchoRadioStation, onFilter: (RadioDirectoryQuery) -> Unit) {
    val tags = remember(station.genre) { station.genre.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct().take(8) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (station.countryCode.isNotBlank()) AssistChip(onClick = { onFilter(RadioDirectoryQuery(countryCode = station.countryCode)) }, label = { Text(station.countryCode) })
        tags.forEach { tag -> AssistChip(onClick = { onFilter(RadioDirectoryQuery(tag = tag)) }, label = { Text(tag) }) }
    }
}
