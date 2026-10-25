package app.echo.android.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.lyrics.EchoLyricsTextDecoder
import app.echo.android.model.lyrics.EchoLyrics
import kotlinx.coroutines.CancellationException

/** The host performs I/O; confirming saves the exact document shown in this preview. */
@Composable
fun LyricsImportPreviewDialog(
    sourceKey: String,
    loadPreview: suspend (String?) -> EchoLyrics,
    onImport: (EchoLyrics) -> Unit,
    onDismiss: () -> Unit,
) {
    var encoding by remember(sourceKey) { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val loader by rememberUpdatedState(loadPreview)
    val preview by produceState(ImportPreview(), sourceKey, encoding) {
        value = ImportPreview()
        try {
            value = ImportPreview(sourceKey, encoding, lyrics = loader(encoding))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            value = ImportPreview(sourceKey, encoding, error = error.message)
        }
    }
    val current = preview.sourceKey == sourceKey && preview.encoding == encoding
    val lyrics = preview.lyrics.takeIf { current }
    val autoLabel = stringResource(R.string.lyrics_import_encoding_auto)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lyrics_import_preview_title)) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.lyrics_import_encoding_hint))
                Box {
                    OutlinedButton(onClick = { menuOpen = true }) {
                        Text(stringResource(R.string.lyrics_import_encoding, encoding ?: autoLabel))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        (listOf<String?>(null) + EchoLyricsTextDecoder.selectableCharsets).forEach { name ->
                            DropdownMenuItem(text = { Text(name ?: autoLabel) }, onClick = { encoding = name; menuOpen = false })
                        }
                    }
                }
                if (!current) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (current && lyrics == null) Text(preview.error ?: stringResource(R.string.lyrics_import_preview_failed), color = MaterialTheme.colorScheme.error)
                lyrics?.metadata?.get("text_encoding")?.let { Text(stringResource(R.string.lyrics_decoding_encoding, it), style = MaterialTheme.typography.labelMedium) }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(lyrics?.lines?.take(12).orEmpty()) { _, line ->
                        Column {
                            Text(line.text)
                            line.translation?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = lyrics != null, onClick = { lyrics?.let(onImport) }) { Text(stringResource(R.string.lyrics_manager_import)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

private data class ImportPreview(
    val sourceKey: String? = null,
    val encoding: String? = null,
    val lyrics: EchoLyrics? = null,
    val error: String? = null,
)
