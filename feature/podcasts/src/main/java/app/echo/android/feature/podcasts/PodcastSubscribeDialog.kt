package app.echo.android.feature.podcasts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.radio.EchoRadioStation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PodcastSubscribeDialog(subscribe: suspend (String) -> Unit, onDismiss: () -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(stringResource(R.string.podcast_subscribe)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.podcast_notice))
                OutlinedTextField(url, { url = it.take(4096); failed = false }, singleLine = true, enabled = !busy,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                    label = { Text(stringResource(R.string.podcast_rss_url)) },
                    isError = failed || (url.isNotBlank() && !EchoRadioStation.validUrl(url)))
                if (busy) LinearProgressIndicator()
                if (failed) Text(stringResource(R.string.podcast_error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && EchoRadioStation.validUrl(url), onClick = {
                busy = true
                scope.launch {
                    try { subscribe(url); onDismiss() }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { failed = true }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.podcast_subscribe)) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.podcast_cancel)) } })
}
