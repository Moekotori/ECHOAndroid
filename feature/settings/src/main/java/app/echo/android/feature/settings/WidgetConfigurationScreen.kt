package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import app.echo.android.model.settings.EchoWidgetOptions
import app.echo.android.model.settings.EchoWidgetPlaylistOption

@Composable
fun WidgetConfigurationScreen(initial: EchoWidgetOptions, playlists: List<EchoWidgetPlaylistOption>,
    loading: Boolean, busy: Boolean, error: Boolean, query: String, onQuery: (String) -> Unit,
    onSave: (EchoWidgetOptions) -> Unit, onCancel: () -> Unit,
    title: String, artist: String, lyric: String?) {
    var options by rememberSaveable(initial, stateSaver = WidgetOptionsSaver) { mutableStateOf(initial) }
    var picker by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(scheme.background).safeDrawingPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.widget_config_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.widget_config_hint), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        // A quiet record sleeve preview is the visual focus; controls never start playback here.
        Surface(shape = MaterialTheme.shapes.large, color = scheme.surfaceVariant.copy(alpha = options.backgroundOpacity),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (options.showArtwork) Surface(shape = MaterialTheme.shapes.medium, color = scheme.surface) {
                        EchoIcon(Icons.Rounded.MusicNote, null, Modifier.padding(16.dp).size(28.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(artist, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                if (options.showLyrics) Text(lyric ?: stringResource(R.string.widget_config_lyrics_preview),
                    style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, maxLines = 2)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    if (options.fullControls) EchoIcon(Icons.Rounded.SkipPrevious, null, Modifier.padding(10.dp).size(24.dp))
                    EchoIcon(Icons.Rounded.PlayArrow, null, Modifier.padding(10.dp).size(24.dp))
                    if (options.fullControls) EchoIcon(Icons.Rounded.SkipNext, null, Modifier.padding(10.dp).size(24.dp))
                }
                options.playlistName?.let { Text(stringResource(R.string.widget_config_playlist_preview, it), style = MaterialTheme.typography.labelMedium) }
            }
        }
        WidgetConfigurationToggle(stringResource(R.string.widget_config_artwork), options.showArtwork) { options = options.copy(showArtwork = it) }
        WidgetConfigurationToggle(stringResource(R.string.widget_config_lyrics), options.showLyrics) { options = options.copy(showLyrics = it) }
        WidgetConfigurationToggle(stringResource(R.string.widget_config_controls), options.fullControls) { options = options.copy(fullControls = it) }
        Column {
            Text(stringResource(R.string.widget_config_opacity, (options.backgroundOpacity * 100).toInt()), style = MaterialTheme.typography.labelLarge)
            Slider(options.backgroundOpacity, onValueChange = { options = options.copy(backgroundOpacity = it) }, steps = 3)
        }
        HorizontalDivider()
        Text(stringResource(R.string.widget_config_playlist), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.widget_config_playlist_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { picker = true }) {
                Text(options.playlistName ?: stringResource(R.string.widget_config_none))
            }
            EchoDropdownMenu(picker, onDismissRequest = { picker = false }) {
                EchoDropdownMenuItem(selected = options.playlistMediaId == null, text = { Text(stringResource(R.string.widget_config_none)) }, onClick = {
                    options = options.copy(playlistMediaId = null, playlistName = null); picker = false
                })
                playlists.forEach { playlist -> EchoDropdownMenuItem(selected = options.playlistMediaId == playlist.mediaId, text = { Text(playlist.name, maxLines = 1) }, onClick = {
                    options = options.copy(playlistMediaId = playlist.mediaId, playlistName = playlist.name); picker = false
                }) }
            }
        }
        OutlinedTextField(query, onValueChange = onQuery, singleLine = true, label = { Text(stringResource(R.string.widget_config_search)) }, modifier = Modifier.fillMaxWidth())
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!loading && playlists.isEmpty()) Text(stringResource(R.string.widget_config_empty), style = MaterialTheme.typography.bodySmall)
        if (error) Text(stringResource(R.string.widget_config_error), color = scheme.error, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel, enabled = !busy) { Text(stringResource(R.string.widget_config_cancel)) }
            Button(onClick = { onSave(options.normalized()) }, enabled = !busy) {
                Text(stringResource(R.string.widget_config_save))
            }
        }
    }
}

private val WidgetOptionsSaver = listSaver<EchoWidgetOptions, Any>(
    save = { listOf(it.showArtwork, it.showLyrics, it.fullControls, it.backgroundOpacity,
        it.playlistMediaId.orEmpty(), it.playlistName.orEmpty()) },
    restore = { EchoWidgetOptions(it[0] as Boolean, it[1] as Boolean, it[2] as Boolean,
        it[3] as Float, (it[4] as String).takeIf(String::isNotEmpty), (it[5] as String).takeIf(String::isNotEmpty)).normalized() },
)

@Composable
private fun WidgetConfigurationToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onCheckedChange = onChange)
    }
}
