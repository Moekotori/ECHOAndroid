package app.echo.android.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.PlaylistExportFormat

@Composable
fun PlaylistExportDialog(name: String, onChoose: (PlaylistExportFormat) -> Unit, onDismiss: () -> Unit) {
    var selected by rememberSaveable(name) { mutableStateOf(PlaylistExportFormat.M3u) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.playlist_export_format)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                LazyColumn(Modifier.heightIn(max = 360.dp).selectableGroup()) {
                    items(PlaylistExportFormat.entries, key = { it.name }) { format ->
                        val checked = format == selected
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = if (checked) MaterialTheme.colorScheme.surfaceContainerHighest
                                else androidx.compose.ui.graphics.Color.Transparent,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .selectable(checked, role = Role.RadioButton, onClick = { selected = format })
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                RadioButton(selected = checked, onClick = null)
                                Text(when (format) {
                                    PlaylistExportFormat.M3u -> stringResource(R.string.playlist_export_m3u_label)
                                    PlaylistExportFormat.Text -> stringResource(R.string.playlist_export_text_label)
                                    PlaylistExportFormat.Csv -> stringResource(R.string.playlist_export_csv_label)
                                    PlaylistExportFormat.Json -> stringResource(R.string.playlist_export_json_label)
                                    else -> format.extension.uppercase()
                                })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoose(selected) }) { Text(stringResource(R.string.playlist_export_choose_location)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_library_cancel_4c5fa5)) }
        },
    )
}
