package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.*
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun NavidromeReceiptsDialog(actions: NavidromeActions, onDismiss: () -> Unit) {
    var rows by remember { mutableStateOf<List<NavidromeReceipt>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var retry by remember { mutableStateOf<NavidromeReceipt?>(null) }
    val scope = rememberCoroutineScope()
    fun resolve(row: NavidromeReceipt, resolution: NavidromeReceiptResolution) {
        if (busy) return
        busy = true; failed = false
        scope.launch {
            try { actions.resolveReceipt(row.key, resolution); revision++ }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    LaunchedEffect(actions, revision) {
        loading = true
        try { rows = actions.receipts() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.nav_receipts)) }, text = {
        LazyColumn(Modifier.heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(stringResource(R.string.nav_receipts_hint)) }
            if (busy || loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (failed) item { Text(stringResource(R.string.nav_error), color = MaterialTheme.colorScheme.error) }
            if (!loading && rows.isEmpty()) item { Text(stringResource(R.string.nav_empty)) }
            items(rows, key = { it.key }) { row ->
                Column {
                    Text(row.songId, style = MaterialTheme.typography.titleSmall)
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(row.timestampMs)), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(when (row.state) {
                        NavidromeReceiptState.Pending -> R.string.nav_receipt_pending
                        NavidromeReceiptState.Sending, NavidromeReceiptState.Unknown -> R.string.nav_receipt_unknown
                        NavidromeReceiptState.Rejected -> R.string.nav_receipt_rejected
                    }), style = MaterialTheme.typography.bodySmall)
                    Row {
                        TextButton(enabled = !busy, onClick = { retry = row }) { Text(stringResource(R.string.remote_playlist_retry)) }
                        TextButton(enabled = !busy, onClick = { resolve(row, NavidromeReceiptResolution.Confirmed) }) { Text(stringResource(R.string.nav_receipt_confirmed)) }
                        TextButton(enabled = !busy, onClick = { resolve(row, NavidromeReceiptResolution.Ignore) }) { Text(stringResource(R.string.nav_receipt_ignore)) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.remote_playlist_close)) } })
    retry?.let { row -> AlertDialog(onDismissRequest = { retry = null }, title = { Text(stringResource(R.string.remote_playlist_retry)) },
        text = { Text(stringResource(R.string.nav_receipt_retry_confirm)) },
        confirmButton = { TextButton(onClick = { retry = null; resolve(row, NavidromeReceiptResolution.Retry) }) { Text(stringResource(R.string.remote_playlist_retry)) } },
        dismissButton = { TextButton(onClick = { retry = null }) { Text(stringResource(R.string.remote_playlist_close)) } }) }
}
