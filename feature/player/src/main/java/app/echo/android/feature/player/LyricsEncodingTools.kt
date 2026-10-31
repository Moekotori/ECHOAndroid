package app.echo.android.feature.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.EchoTextEncodings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LyricsEncodingTools(read: suspend (String, String?) -> String,
    apply: suspend (String, String?) -> Boolean) {
    var uri by remember { mutableStateOf<String?>(null) }
    var charset by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val choose = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected ->
        if (selected != null) { uri = selected.toString(); charset = null }
    }
    TextButton(onClick = { choose.launch(arrayOf("text/*", "application/octet-stream", "*/*")) }) {
        Text(stringResource(R.string.lyrics_encoding_title))
    }
    uri?.let { source ->
        LaunchedEffect(source, charset) {
            preview = null; failed = false
            try { preview = read(source, charset) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
        }
        AlertDialog(onDismissRequest = { if (!saving) uri = null }, title = { Text(stringResource(R.string.lyrics_encoding_title)) },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.lyrics_encoding_note))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = charset == null, enabled = !saving, onClick = { charset = null }, label = { Text(stringResource(R.string.lyrics_encoding_auto)) })
                    EchoTextEncodings.names.forEach { name -> FilterChip(selected = charset == name, enabled = !saving,
                        onClick = { charset = name }, label = { Text(name) }) }
                }
                preview?.let { Text(it.take(2500)) }
                if (preview == null && !failed || saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (failed) Text(stringResource(R.string.lyrics_encoding_failed), color = MaterialTheme.colorScheme.error)
            } }, confirmButton = { TextButton(enabled = preview != null && !saving, onClick = {
                saving = true
                scope.launch {
                    try { if (apply(source, charset)) uri = null else failed = true }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true }
                    finally { saving = false }
                }
            }) { Text(stringResource(R.string.lyrics_encoding_apply)) } }, dismissButton = {
                TextButton(enabled = !saving, onClick = { uri = null }) { Text(stringResource(R.string.lyrics_encoding_close)) }
            })
    }
}
