package app.echo.android.feature.connect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.model.connect.EchoSongTransferState

@Composable
fun EchoLinkSongTransferDialog(state: EchoSongTransferState, connected: Boolean, scanning: Boolean,
    onSend: () -> Unit, onChoose: () -> Unit, onStart: () -> Unit, onStop: () -> Unit,
    onScan: () -> Unit, onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.link_transfer_title)) },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.link_transfer_detail), style = MaterialTheme.typography.bodySmall)
            if (state.starting) LinearProgressIndicator(Modifier.fillMaxWidth())
            FilledTonalButton(onClick = onSend, enabled = state.active && !state.sending && state.current == null,
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.link_transfer_send)) }
            HorizontalDivider()
            Text(stringResource(R.string.link_transfer_receive), style = MaterialTheme.typography.titleSmall)
            Text(state.directory ?: stringResource(R.string.link_transfer_no_directory), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onChoose, enabled = !state.sending && state.current == null) { Text(stringResource(R.string.link_transfer_choose)) }
                TextButton(onClick = onScan, enabled = state.received > 0 && !scanning) { Text(stringResource(R.string.link_transfer_scan)) }
            }
            val current = state.current
            current?.let { item ->
                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(if (item.direction == "to-phone") R.string.link_transfer_incoming else R.string.link_transfer_outgoing), style = MaterialTheme.typography.labelSmall)
                LinearProgressIndicator(progress = { (item.bytes.toFloat() / item.size.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${item.bytes * 100 / item.size.coerceAtLeast(1)}%", style = MaterialTheme.typography.labelSmall)
            }
            val shown = remember(state.items, current?.id, current?.direction) {
                state.items.asReversed().filter { it.id != current?.id && !(current?.direction == "to-pc" && it.direction == "to-pc" && it.state == "transferring") }.take(12)
            }
            shown.forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(when (item.state) {
                            "completed" -> R.string.link_transfer_saved
                            "failed" -> R.string.link_transfer_failed
                            "cancelled" -> R.string.link_transfer_cancelled
                            "transferring" -> R.string.link_transfer_working
                            else -> R.string.link_transfer_waiting
                        }), style = MaterialTheme.typography.labelSmall)
                    }
                }
            state.error?.let { error -> Text(stringResource(when (error) {
                "connection" -> R.string.link_transfer_connection
                "directory" -> R.string.link_transfer_directory_error
                "unsupported" -> R.string.link_transfer_unsupported
                "files" -> R.string.link_transfer_files_error
                else -> R.string.link_transfer_error
            }), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        } },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.link_transfer_close)) } },
        dismissButton = { TextButton(onClick = if (state.active || state.starting) onStop else onStart,
            enabled = connected || state.active || state.starting) { Text(stringResource(if (state.active || state.starting) R.string.link_transfer_stop else R.string.link_transfer_start)) } })
}
