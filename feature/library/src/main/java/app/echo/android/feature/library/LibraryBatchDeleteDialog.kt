package app.echo.android.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

data class LibraryBatchDeleteResult(val removedIds: Set<String>, val cancelled: Boolean = false)

@Composable
internal fun LibraryBatchDeleteDialog(
    count: Int,
    canDeleteOriginals: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit,
) {
    var deleteOriginals by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.batch_delete_confirm, count, count)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.batch_delete_index_hint))
                Row(
                    Modifier.fillMaxWidth().toggleable(
                        value = deleteOriginals,
                        enabled = canDeleteOriginals,
                        role = Role.Checkbox,
                        onValueChange = { deleteOriginals = it },
                    ).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = deleteOriginals, onCheckedChange = null, enabled = canDeleteOriginals)
                    Text(stringResource(R.string.batch_delete_originals), modifier = Modifier.weight(1f))
                }
                Text(
                    stringResource(if (canDeleteOriginals) R.string.batch_delete_file_hint else R.string.batch_delete_file_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (deleteOriginals) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteOriginals && canDeleteOriginals) }) {
                Text(stringResource(if (deleteOriginals) R.string.batch_delete_files else R.string.batch_remove_index), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.batch_cancel)) } },
    )
}
