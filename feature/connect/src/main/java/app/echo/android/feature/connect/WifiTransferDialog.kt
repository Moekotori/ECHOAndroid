package app.echo.android.feature.connect

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.connect.EchoWifiTransferState

@Composable
fun WifiTransferEntry(onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(stringResource(R.string.wifi_transfer_title)) }
}

@Composable
fun WifiTransferDialog(state: EchoWifiTransferState, onChoose: () -> Unit, onStart: () -> Unit,
    onStop: () -> Unit, onScan: () -> Unit, scanning: Boolean, onClose: () -> Unit) {
    val running = state.phase == "running"
    val starting = state.phase == "starting"
    AlertDialog(onDismissRequest = onClose, title = { Text(stringResource(R.string.wifi_transfer_title)) },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.wifi_transfer_detail))
            Text(state.directory ?: stringResource(R.string.wifi_transfer_no_directory))
            TextButton(enabled = !running && !starting, onClick = onChoose) { Text(stringResource(R.string.wifi_transfer_choose)) }
            state.address?.let { address ->
                SelectionContainer { Text(address, style = MaterialTheme.typography.titleMedium) }
                Text(stringResource(R.string.wifi_transfer_code, state.pairingCode.orEmpty()), style = MaterialTheme.typography.titleLarge)
            }
            if (starting) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.phase == "expired") Text(stringResource(R.string.wifi_transfer_expired))
            state.receiving?.let { Text(stringResource(R.string.wifi_transfer_receiving, it)) }
            Text(stringResource(R.string.wifi_transfer_counts, state.completed, state.failed))
            if (state.error != null) Text(stringResource(when (state.error) {
                "no_lan" -> R.string.wifi_transfer_no_lan
                "directory" -> R.string.wifi_transfer_directory_error
                "cleanup" -> R.string.wifi_transfer_cleanup_error
                else -> R.string.wifi_transfer_start_error
            }), color = MaterialTheme.colorScheme.error)
            state.results.forEach { result ->
                Text(stringResource(if (result.success) R.string.wifi_transfer_result_saved else R.string.wifi_transfer_result_failed, result.name),
                    style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.wifi_transfer_limits), style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = state.directory != null && state.receiving == null && !scanning, onClick = onScan) {
                Text(stringResource(if (scanning) R.string.wifi_transfer_scanning else R.string.wifi_transfer_scan))
            }
        } },
        confirmButton = { TextButton(enabled = state.directory != null && !starting,
            onClick = if (running) onStop else onStart) {
            Text(stringResource(if (running) R.string.wifi_transfer_stop else R.string.wifi_transfer_start))
        } },
        dismissButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.wifi_transfer_close)) } })
}
