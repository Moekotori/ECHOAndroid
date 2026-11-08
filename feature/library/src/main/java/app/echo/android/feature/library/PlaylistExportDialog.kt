package app.echo.android.feature.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.echo.android.model.library.PlaylistExportFormat

@Composable
fun PlaylistExportDialog(name: String, onChoose: (PlaylistExportFormat) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.playlist_export_format)) },
        text = { Column {
            Text(name)
            PlaylistExportFormat.entries.forEach { format ->
                TextButton(onClick = { onChoose(format) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(when (format) {
                        PlaylistExportFormat.M3u -> R.string.playlist_export_m3u_label
                        PlaylistExportFormat.Text -> R.string.playlist_export_text_label
                        PlaylistExportFormat.Csv -> R.string.playlist_export_csv_label
                    }))
                }
            }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_library_cancel_4c5fa5)) } })
}
