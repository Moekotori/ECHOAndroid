package app.echo.android.feature.radio

import androidx.compose.foundation.layout.*
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
                Text(station.name, style = MaterialTheme.typography.headlineSmall)
            }
            EchoArtworkImage(station.artworkUrl, null, Modifier.size(128.dp), sizeClass = app.echo.android.design.EchoArtworkSize.Card)
            Text(listOf(station.countryCode, station.language, station.genre).filter { it.isNotBlank() }.joinToString(" · "))
            streamTitle?.takeIf { it != station.name && it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            Row {
                if (station.countryCode.isNotBlank()) TextButton(onClick = { onFilter(RadioDirectoryQuery(countryCode = station.countryCode)) }) { Text(station.countryCode) }
                station.genre.split(',').map { it.trim() }.filter { it.isNotBlank() }.take(2).forEach { tag ->
                    TextButton(onClick = { onFilter(RadioDirectoryQuery(tag = tag)) }) { Text(tag) }
                }
            }
            if (station.description.isNotBlank()) Text(station.description, Modifier.padding(vertical = 8.dp))
            Row {
                Button(enabled = !pending, onClick = { if (playing) actions.stop() else actions.play(station, null) }) { Text(stringResource(if (playing) R.string.radio_stop else R.string.radio_play)) }
                if (saved) IconButton(onClick = onEdit) { EchoIcon(Icons.Rounded.Edit, stringResource(R.string.radio_edit)) }
                else TextButton(onClick = { save(station) }) { Text(stringResource(R.string.radio_save)) }
                station.homepage?.let { url -> IconButton(onClick = { actions.openUrl(url) }) { EchoIcon(Icons.Rounded.OpenInNew, stringResource(R.string.radio_website)) } }
            }
            if (failed) Text(stringResource(R.string.radio_error), color = MaterialTheme.colorScheme.error)
            if (playing) station.videoUrl?.let { videoSurface(it) }
            Text(stringResource(R.string.radio_streams), style = MaterialTheme.typography.titleMedium)
        }
        items(streams, key = { it.url }) { stream ->
            ListItem(headlineContent = { Text(listOfNotNull(stream.codec, stream.bitrateKbps?.let { "$it kbps" }).joinToString(" · ").ifBlank { stringResource(R.string.radio_network_stream) }) },
                supportingContent = { Text(stream.url) }, leadingContent = {
                    IconButton(enabled = !pending, onClick = { actions.play(station, stream.url) }) { EchoIcon(Icons.Rounded.PlayArrow, stringResource(R.string.radio_play)) }
                }, trailingContent = {
                    if (stream.url == (station.defaultStreamUrl ?: station.url)) Text(stringResource(R.string.radio_default))
                    else if (saved) TextButton(onClick = { save(station.copy(defaultStreamUrl = stream.url)) }) { Text(stringResource(R.string.radio_make_default)) }
                }, colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
        }
    }
}
