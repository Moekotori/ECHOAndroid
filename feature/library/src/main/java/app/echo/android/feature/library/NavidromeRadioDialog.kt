package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.*
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun NavidromeRadioDialog(actions: NavidromeActions, entry: NavidromeEntry?, onChanged: () -> Unit, onDismiss: () -> Unit) {
    var name by remember(entry) { mutableStateOf(entry?.name.orEmpty()) }
    var url by remember(entry) { mutableStateOf(entry?.url.orEmpty()) }
    var homepage by remember(entry) { mutableStateOf(entry?.homepage.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun execute(command: NavidromeCommand) {
        if (busy) return
        busy = true; failed = false
        scope.launch {
            try { actions.execute(command); onChanged(); onDismiss() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.nav_radio)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(200) }, enabled = !busy, singleLine = true, label = { Text(stringResource(R.string.nav_name)) })
            OutlinedTextField(url, { url = it.take(4000) }, enabled = !busy, singleLine = true, label = { Text(stringResource(R.string.nav_stream_url)) })
            OutlinedTextField(homepage, { homepage = it.take(4000) }, enabled = !busy, singleLine = true, label = { Text(stringResource(R.string.nav_homepage)) })
            if (failed) Text(stringResource(R.string.nav_error), color = MaterialTheme.colorScheme.error)
            if (entry != null) TextButton(enabled = !busy, onClick = {
                if (deleting) execute(NavidromeCommand.DeleteRadio(entry.id)) else deleting = true
            }) { Text(stringResource(if (deleting) R.string.nav_confirm_delete else R.string.remote_playlist_delete), color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(enabled = !busy && name.isNotBlank() && webUrl(url) && (homepage.isBlank() || webUrl(homepage)),
            onClick = { execute(NavidromeCommand.Radio(entry?.id, name, url, homepage)) }) { Text(stringResource(R.string.remote_playlist_save)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.remote_playlist_close)) } })
}

private fun webUrl(value: String) = runCatching { URI(value.trim()).let {
    it.scheme?.lowercase() in listOf("http", "https") && !it.host.isNullOrBlank() && it.userInfo == null
} }.getOrDefault(false)
