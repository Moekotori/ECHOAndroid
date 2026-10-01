package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import app.echo.android.design.EchoIcon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.echo.android.model.settings.EchoLyricsOptions
import app.echo.android.model.settings.EchoLyricsSource

@Composable
internal fun SettingsLyricsSourceRow(options: EchoLyricsOptions, onChange: (EchoLyricsOptions) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val labels = EchoLyricsSource.entries.associateWith { source ->
        stringResource(when (source) {
            EchoLyricsSource.Spl -> R.string.settings_lyrics_source_spl
            EchoLyricsSource.Sidecar -> R.string.settings_lyrics_source_sidecar
            EchoLyricsSource.Embedded -> R.string.settings_lyrics_source_embedded
        })
    }
    SettingsActionRow(
        title = stringResource(R.string.settings_lyrics_source_order),
        detail = options.sourceOrder.joinToString(" > ") { labels.getValue(it) },
        onClick = { open = true },
    )
    if (!open) return
    var draft by remember(options.sourceOrder) { mutableStateOf(options.sourceOrder) }
    fun move(from: Int, to: Int) {
        draft = draft.toMutableList().apply { add(to, removeAt(from)) }
    }
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.settings_lyrics_source_order)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_lyrics_source_order_detail))
                draft.forEachIndexed { index, source ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(labels.getValue(source), Modifier.weight(1f))
                        IconButton(onClick = { move(index, index - 1) }, enabled = index > 0) {
                            EchoIcon(Icons.Rounded.KeyboardArrowUp, stringResource(R.string.settings_lyrics_source_up))
                        }
                        IconButton(onClick = { move(index, index + 1) }, enabled = index < draft.lastIndex) {
                            EchoIcon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.settings_lyrics_source_down))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onChange(options.copy(sourceOrder = draft)); open = false }) {
            Text(stringResource(R.string.settings_lyrics_apply))
        } },
        dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.settings_lyrics_cancel)) } },
    )
}
