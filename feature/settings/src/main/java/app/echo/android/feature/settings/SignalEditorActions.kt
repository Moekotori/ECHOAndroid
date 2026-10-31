package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

/** Instructions stay available without pushing the actual tuning controls down the page. */
@Composable
internal fun SignalEditorActions(
    title: String,
    detail: String,
    canUndo: Boolean,
    onUndo: () -> Unit,
    hint: String? = null,
) {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(hint.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = onUndo, enabled = canUndo) {
            EchoIcon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.sound_undo_edit), Modifier.size(20.dp))
        }
        IconButton(onClick = { showHelp = true }) {
            EchoIcon(Icons.Outlined.Info, stringResource(R.string.sound_edit_help, title), Modifier.size(20.dp))
        }
    }
    if (showHelp) AlertDialog(
        onDismissRequest = { showHelp = false },
        title = { Text(title) }, text = { Text(detail) },
        confirmButton = { TextButton(onClick = { showHelp = false }) {
            Text(stringResource(R.string.sound_help_close))
        } },
    )
}
