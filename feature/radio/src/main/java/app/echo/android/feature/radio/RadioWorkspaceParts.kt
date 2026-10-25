package app.echo.android.feature.radio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

@Composable
internal fun RadioSectionHeading(title: String, onMore: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (onMore != null) TextButton(onClick = onMore) { Text(stringResource(R.string.radio_browse_all)) }
    }
}

@Composable
internal fun RadioError(onRetry: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.radio_error), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.radio_retry)) }
        }
    }
}

@Composable
internal fun RadioEmptyState(onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            EchoIcon(Icons.Rounded.Radio, null, Modifier.padding(24.dp).size(48.dp))
        }
        Text(stringResource(R.string.radio_empty_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.radio_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = onAdd) { Text(stringResource(R.string.radio_add)) }
    }
}

@Composable
internal fun RadioPagination(directory: RadioDiscoveryState, actions: RadioActions) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(enabled = directory.query.page > 0 && !directory.loading, onClick = { actions.browse(directory.query.copy(page = directory.query.page - 1)) }) {
            EchoIcon(Icons.Rounded.ChevronLeft, stringResource(R.string.radio_previous))
        }
        Text(stringResource(R.string.radio_page, directory.query.page + 1), style = MaterialTheme.typography.labelLarge)
        IconButton(enabled = directory.hasNext && !directory.loading, onClick = { actions.browse(directory.query.copy(page = directory.query.page + 1)) }) {
            EchoIcon(Icons.Rounded.ChevronRight, stringResource(R.string.radio_next))
        }
    }
}

@Composable
internal fun RadioAddOptions(onDismiss: () -> Unit, onExplore: () -> Unit, onManual: () -> Unit, onClipboard: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.radio_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.radio_add_hint), style = MaterialTheme.typography.bodyMedium)
                listOf(Triple(R.string.radio_global, Icons.Rounded.Public, onExplore), Triple(R.string.radio_enter_url, Icons.Rounded.Link, onManual),
                    Triple(R.string.radio_play_clipboard, Icons.Rounded.ContentPaste, onClipboard)).forEach { (label, icon, action) ->
                    OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                        EchoIcon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(stringResource(label), Modifier.weight(1f))
                    }
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.radio_cancel)) } })
}

@Composable
internal fun RadioNoResults(onReset: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EchoIcon(Icons.Rounded.SearchOff, null, Modifier.size(32.dp))
        Text(stringResource(R.string.radio_no_results), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.radio_no_results_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onReset) { Text(stringResource(R.string.radio_reset_search)) }
    }
}
