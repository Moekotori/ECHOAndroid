package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoDropdownMenu
import app.echo.android.design.EchoDropdownMenuItem
import app.echo.android.model.settings.EchoWidgetOptions
import app.echo.android.model.settings.EchoWidgetPlaylistOption
import app.echo.android.design.R as DesignR

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
        WidgetConfigurationPreview(options, title, artist, lyric)
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

/** Static layout sample; all controls here are decorative and never send playback commands. */
@Composable
private fun WidgetConfigurationPreview(options: EchoWidgetOptions, title: String, artist: String, lyric: String?) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val surface = if (dark) Color(0xFF202429) else Color(0xFFF8F9FA)
    val foreground = if (dark) Color(0xFFF2F4F6) else Color(0xFF202429)
    val secondary = if (dark) Color(0xFFA9B1B9) else Color(0xFF646B73)
    Surface(shape = RoundedCornerShape(28.dp), color = surface.copy(alpha = options.backgroundOpacity),
        contentColor = foreground, modifier = Modifier.fillMaxWidth()) {
        Column {
            if (options.showArtwork) Box(Modifier.fillMaxWidth().height(152.dp)
                .background(if (dark) Color(0xFF30373E) else Color(0xFFE4E8EB)), contentAlignment = Alignment.Center) {
                EchoIcon(Icons.Rounded.MusicNote, null, Modifier.size(48.dp), tint = secondary)
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Text(artist, style = MaterialTheme.typography.bodyMedium, color = secondary, maxLines = 1)
                if (options.showLyrics) Text(lyric ?: stringResource(R.string.widget_config_lyrics_preview),
                    style = MaterialTheme.typography.bodySmall, color = secondary, maxLines = 1)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                    if (options.fullControls) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        EchoIcon(painterResource(DesignR.drawable.echo_widget_ic_previous), null, Modifier.size(26.dp), tint = foreground)
                    }
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        EchoIcon(painterResource(DesignR.drawable.echo_widget_ic_play), null, Modifier.size(32.dp), tint = foreground)
                    }
                    if (options.fullControls) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        EchoIcon(painterResource(DesignR.drawable.echo_widget_ic_next), null, Modifier.size(26.dp), tint = foreground)
                    }
                }
                options.playlistName?.let {
                    Text(stringResource(R.string.widget_config_playlist_preview, it),
                        style = MaterialTheme.typography.labelMedium, color = secondary, maxLines = 1)
                }
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
