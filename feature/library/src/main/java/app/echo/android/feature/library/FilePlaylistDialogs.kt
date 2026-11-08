package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun FilePlaylistCreateDialog(onCreate: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.file_playlist_create)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.file_playlist_hint))
            OutlinedTextField(name, { name = it.take(80) }, label = { Text(stringResource(R.string.playlist_import_name)) }, singleLine = true)
            TextButton(onClick = { onCreate(name.trim(), false) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.file_playlist_files)) }
            TextButton(onClick = { onCreate(name.trim(), true) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.file_playlist_folder)) }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_library_cancel_4c5fa5)) } })
}

@Composable
fun FilePlaylistProgressDialog(progress: Pair<Int, Int>?, error: Boolean, onDismiss: () -> Unit, adding: Boolean = false) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(if (adding) R.string.file_playlist_add_files else R.string.file_playlist_create)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (error) Text(stringResource(R.string.file_playlist_failed)) else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.file_playlist_progress, progress?.first ?: 0, progress?.second ?: 0))
            }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_library_cancel_4c5fa5)) } })
}
