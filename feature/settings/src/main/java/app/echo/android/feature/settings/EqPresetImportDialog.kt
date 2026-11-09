package app.echo.android.feature.settings

import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoEqualizerShareCodec
import app.echo.android.model.playback.EchoEqualizerUserPreset
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MaxEqImportBytes = 64 * 1024

@Composable
internal fun EqPresetImportDialog(
    canImport: Boolean,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var raw by remember { mutableStateOf("") }
    var reading by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<Int?>(null) }
    val decoded by produceState<Pair<String, EchoEqualizerUserPreset?>?>(null, raw) {
        val text = raw
        value = if (text.isBlank()) null else text to withContext(Dispatchers.Default) {
            runCatching { EchoEqualizerShareCodec.decode(text, "import") }.getOrNull()
        }
    }
    val current = decoded?.takeIf { it.first == raw }
    val preview = current?.second

    fun accept(text: String) {
        if (text.length <= MaxEqImportBytes) {
            raw = text.removePrefix("\uFEFF")
            inputError = null
        } else {
            inputError = R.string.eq_import_too_large
        }
    }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            reading = true
            inputError = null
            scope.launch {
                try {
                    val text = readEqImportText(context.contentResolver, uri)
                    if (text == null) inputError = R.string.eq_import_too_large else accept(text)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    inputError = R.string.eq_import_file_error
                } finally {
                    reading = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.eq_share_import_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        enabled = !reading,
                        onClick = { pickFile.launch(arrayOf("text/*", "application/octet-stream")) },
                    ) { Text(stringResource(R.string.eq_import_choose_file)) }
                    TextButton(enabled = !reading, onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.let(::accept)
                    }) { Text(stringResource(R.string.eq_share_paste)) }
                }
                OutlinedTextField(
                    value = raw,
                    onValueChange = ::accept,
                    enabled = !reading,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.eq_share_import_hint)) },
                    minLines = 3,
                    maxLines = 7,
                )
                val error = inputError
                when {
                    reading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    error != null -> SignalNote(stringResource(error), error = true)
                    preview != null -> {
                        val count = if (preview.parametric) preview.filters.size else preview.gainsDb.size
                        SignalNote(stringResource(R.string.eq_share_preview, preview.name, count, formatEqGain(preview.preampDb)))
                    }
                    current != null -> SignalNote(stringResource(R.string.eq_share_import_invalid), error = true)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(raw) },
                enabled = canImport && !reading && inputError == null && preview != null,
            ) { Text(stringResource(R.string.eq_import_save_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.error_log_clear_cancel)) }
        },
    )
}

private suspend fun readEqImportText(resolver: ContentResolver, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        val input = resolver.openInputStream(uri) ?: error("Cannot open EQ document")
        val output = ByteArrayOutputStream()
        input.use {
            val buffer = ByteArray(4096)
            while (true) {
                ensureActive()
                val count = it.read(buffer)
                if (count < 0) break
                check(count > 0)
                if (output.size() + count > MaxEqImportBytes) return@withContext null
                output.write(buffer, 0, count)
            }
        }
        val bytes = output.toByteArray()
        val utf16 = bytes.size >= 2 && (
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ||
                bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()
            )
        val charset = if (utf16) Charsets.UTF_16 else Charsets.UTF_8
        charset.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
    }
