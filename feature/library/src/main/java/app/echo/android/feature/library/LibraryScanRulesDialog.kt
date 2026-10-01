package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.LibraryScanOptions

@Composable
internal fun LibraryScanRulesDialog(
    initialOptions: LibraryScanOptions,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (LibraryScanOptions) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(initialOptions) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.add_music_rules), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge)
                TextButton(enabled = enabled, onClick = { draft = LibraryScanOptions() }) {
                    Text(stringResource(R.string.add_music_reset))
                }
            }
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                LibraryScanFilters(draft, enabled) { draft = it }
            }
        },
        confirmButton = {
            TextButton(enabled = enabled, onClick = { onSave(draft) }) {
                Text(stringResource(R.string.add_music_save_rules))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_library_cancel_4c5fa5)) }
        },
    )
}
