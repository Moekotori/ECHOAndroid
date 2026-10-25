package app.echo.android.feature.radio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.radio.*
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun RadioStationEditor(station: EchoRadioStation?, actions: RadioActions, onDismiss: () -> Unit) {
    var name by rememberSaveable(station?.id) { mutableStateOf(station?.name.orEmpty()) }
    var url by rememberSaveable(station?.id) { mutableStateOf(station?.url.orEmpty()) }
    var cover by rememberSaveable(station?.id) { mutableStateOf(station?.artworkUrl.orEmpty()) }
    var video by rememberSaveable(station?.id) { mutableStateOf(station?.videoUrl.orEmpty()) }
    var description by rememberSaveable(station?.id) { mutableStateOf(station?.description.orEmpty()) }
    var alternatives by rememberSaveable(station?.id) { mutableStateOf(station?.streams?.joinToString("\n") { it.url }.orEmpty()) }
    var probe by remember { mutableStateOf<RadioProbeResult?>(null) }
    var probeUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var advanced by rememberSaveable(station?.id) { mutableStateOf(station?.let {
        !it.artworkUrl.isNullOrBlank() || !it.videoUrl.isNullOrBlank() || it.description.isNotBlank() || it.streams.isNotEmpty()
    } == true) }
    val scope = rememberCoroutineScope()
    val streams = remember(alternatives) { alternatives.lines().map(String::trim).filter(String::isNotEmpty) }
    val valid = EchoRadioStation.validUrl(url) && listOf(cover, video).all { it.isBlank() || EchoRadioStation.validUrl(it) } &&
        streams.size <= 15 && streams.all(EchoRadioStation::validUrl)
    fun draft(): EchoRadioStation {
        val facts = probe?.takeIf { probeUrl == url.trim() }
        val parsed = facts?.stations?.firstOrNull()
        val streamUrl = parsed?.url ?: url.trim()
        val original = station ?: EchoRadioStation(UUID.randomUUID().toString(), "", url.trim())
        val base = if (original.url == streamUrl) original else original.copy(
            genre = "", codec = null, bitrateKbps = null, homepage = null, lastPlayedMs = 0)
        val default = base.defaultStreamUrl?.takeIf { it == streamUrl || it in streams }
        return base.copy(name = name.trim().ifBlank { parsed?.name ?: facts?.name ?: URI(url.trim()).host }, url = streamUrl,
            artworkUrl = cover.trim().takeIf { it.isNotEmpty() }, videoUrl = video.trim().takeIf { it.isNotEmpty() },
            description = description.trim().ifBlank { facts?.description.orEmpty() }, genre = facts?.genre ?: base.genre,
            homepage = facts?.homepage ?: base.homepage, codec = facts?.codec ?: base.codec, bitrateKbps = facts?.bitrateKbps ?: base.bitrateKbps,
            streams = streams.distinct().filter { it != streamUrl }.map { stream -> base.streams.firstOrNull { it.url == stream } ?: EchoRadioStream(stream) },
            defaultStreamUrl = default)
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(if (station == null) R.string.radio_add else R.string.radio_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(url, { url = it.take(4096); probe = null }, singleLine = true, enabled = !busy, label = { Text(stringResource(R.string.radio_url)) })
                TextButton(enabled = !busy && EchoRadioStation.validUrl(url), onClick = {
                    busy = true; failed = false
                    scope.launch {
                        try { probe = actions.probe(url.trim()); probeUrl = url.trim(); if (name.isBlank()) name = probe?.name.orEmpty() }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { failed = true }
                        finally { busy = false }
                    }
                }) { Text(stringResource(R.string.radio_probe)) }
                probe?.let { facts ->
                    Text(listOfNotNull(facts.codec, facts.bitrateKbps?.let { "$it kbps" }).joinToString(" · "))
                    if (facts.stations.size > 1) Text(pluralStringResource(R.plurals.radio_playlist_count, facts.stations.size, facts.stations.size))
                }
                OutlinedTextField(name, { name = it.take(120) }, singleLine = true, enabled = !busy, label = { Text(stringResource(R.string.radio_name)) })
                TextButton(onClick = { advanced = !advanced }) { Text(stringResource(R.string.radio_more_fields)) }
                if (advanced) {
                OutlinedTextField(description, { description = it.take(512) }, enabled = !busy, label = { Text(stringResource(R.string.radio_description)) })
                OutlinedTextField(cover, { cover = it.take(2048) }, singleLine = true, enabled = !busy, label = { Text(stringResource(R.string.radio_cover)) })
                OutlinedTextField(video, { video = it.take(2048) }, singleLine = true, enabled = !busy, label = { Text(stringResource(R.string.radio_video)) })
                OutlinedTextField(alternatives, { alternatives = it.take(65_536) }, enabled = !busy, label = { Text(stringResource(R.string.radio_alternatives)) }, supportingText = { Text(stringResource(R.string.radio_alternatives_hint)) })
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text(stringResource(R.string.radio_error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { Row {
            TextButton(enabled = valid && !busy, onClick = { actions.play(draft(), null); onDismiss() }) { Text(stringResource(R.string.radio_play)) }
            TextButton(enabled = valid && !busy, onClick = {
            busy = true
            scope.launch {
                try {
                    val imported = probe?.takeIf { probeUrl == url.trim() }?.stations.orEmpty()
                    if (station == null && imported.size > 1) actions.importStations(imported) else actions.save(draft())
                    onDismiss()
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { failed = true }
                finally { busy = false }
            }
        }) { Text(stringResource(R.string.radio_save)) } } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.radio_cancel)) } })
}
