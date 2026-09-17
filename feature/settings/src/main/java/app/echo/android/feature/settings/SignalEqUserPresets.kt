package app.echo.android.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoTextButton
import app.echo.android.design.echoClickable
import app.echo.android.model.playback.EchoEqResponsePoint
import app.echo.android.model.playback.EchoEqualizerApoCodec
import app.echo.android.model.playback.EchoEqualizerShareCodec
import app.echo.android.model.playback.EchoEqualizerUserPreset
import app.echo.android.model.playback.EchoEqualizerUserPresets

@Composable
internal fun SignalEqUserPresets(
    presets: List<EchoEqualizerUserPreset>,
    activeId: String?,
    defaultSaveName: String,
    currentShare: EchoEqualizerUserPreset?,
    currentCurve: List<EchoEqResponsePoint> = emptyList(),
    enabled: Boolean,
    onSave: (String) -> Unit,
    onUpdate: () -> Unit = {},
    onApply: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onImportShareCode: (String) -> Unit,
    outputDeviceLabel: String? = null,
    outputBound: Boolean = false,
    onBindToOutput: () -> Unit = {},
    onUnbindFromOutput: () -> Unit = {},
) {
    var editor by remember { mutableStateOf<EqUserPresetEditor?>(null) }
    var sharePreset by remember { mutableStateOf<EchoEqualizerUserPreset?>(null) }
    var shareCurve by remember { mutableStateOf<List<EchoEqResponsePoint>>(emptyList()) }
    var showImport by remember { mutableStateOf(false) }
    val canSave = presets.size < EchoEqualizerUserPresets.MaxCount
    val activeName = presets.firstOrNull { it.id == activeId }?.name
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.eq_user_presets),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (activeId != null) {
                EchoTextButton(
                    text = stringResource(R.string.eq_user_preset_update),
                    onClick = onUpdate,
                    enabled = enabled,
                )
            }
            EchoTextButton(
                text = stringResource(R.string.eq_user_preset_save),
                onClick = { editor = EqUserPresetEditor.Save(defaultSaveName) },
                enabled = enabled && canSave,
            )
        }
        if (!outputDeviceLabel.isNullOrBlank()) {
            when {
                outputBound -> EchoTextButton(
                    text = stringResource(R.string.eq_unbind_output, outputDeviceLabel),
                    onClick = onUnbindFromOutput,
                    enabled = enabled,
                )
                activeId != null -> EchoTextButton(
                    text = stringResource(R.string.eq_bind_output, outputDeviceLabel),
                    onClick = onBindToOutput,
                    enabled = enabled,
                )
            }
        }
        if (activeName != null) {
            SignalNote(stringResource(R.string.eq_user_preset_active, activeName))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { showImport = true }, enabled = enabled && canSave, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(R.string.eq_share_import))
            }
            TextButton(
                onClick = {
                    sharePreset = currentShare
                    shareCurve = currentCurve
                },
                enabled = enabled && currentShare != null,
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Text(stringResource(R.string.eq_share_current))
            }
        }
        if (!canSave) {
            SignalNote(stringResource(R.string.eq_user_preset_full))
        } else if (presets.isEmpty()) {
            SignalNote(stringResource(R.string.eq_user_preset_empty))
        }
        presets.forEach { preset ->
            SignalEqUserPresetRow(
                preset = preset,
                active = preset.id == activeId,
                enabled = enabled,
                onApply = { onApply(preset.id) },
                onRename = { editor = EqUserPresetEditor.Rename(preset.id, preset.name) },
                onDelete = { onDelete(preset.id) },
                onShare = {
                    sharePreset = preset
                    shareCurve = emptyList()
                },
                showActions = true,
            )
        }
    }
    editor?.let { current ->
        EqUserPresetNameDialog(
            title = stringResource(
                if (current is EqUserPresetEditor.Rename) R.string.eq_user_preset_rename else R.string.eq_user_preset_save_title,
            ),
            initialName = current.name,
            onDismiss = { editor = null },
            onConfirm = { name ->
                when (current) {
                    is EqUserPresetEditor.Save -> onSave(name)
                    is EqUserPresetEditor.Rename -> onRename(current.id, name)
                }
                editor = null
            },
        )
    }
    sharePreset?.let { preset ->
        EqShareCodeDialog(preset = preset, curve = shareCurve, onDismiss = { sharePreset = null })
    }
    if (showImport) {
        EqImportShareCodeDialog(
            onDismiss = { showImport = false },
            onImport = { code ->
                onImportShareCode(code)
                showImport = false
            },
        )
    }
}

@Composable
internal fun SignalEqUserPresetRow(
    preset: EchoEqualizerUserPreset,
    active: Boolean,
    enabled: Boolean,
    onApply: () -> Unit,
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {},
    onShare: () -> Unit = {},
    showActions: Boolean = false,
    showEditDelete: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val renameLabel = stringResource(R.string.eq_user_preset_rename)
    val deleteLabel = stringResource(R.string.eq_user_preset_delete)
    val shareLabel = stringResource(R.string.eq_share)
    val detail = if (preset.parametric) {
        stringResource(R.string.eq_user_preset_bands, preset.filters.size)
    } else {
        eqPresetLabel(preset.graphicPresetId)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .echoClickable(enabled = enabled, onClick = onApply)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                preset.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = if (active) scheme.primary else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val source = preset.sourceLabel
            SignalNote(if (preset.fromOpra && source != null) source else detail)
        }
        if (showActions) {
            IconButton(onClick = onShare, enabled = enabled, modifier = Modifier.semantics { contentDescription = shareLabel }) {
                Icon(Icons.Outlined.Share, contentDescription = null)
            }
            if (showEditDelete) {
                IconButton(onClick = onRename, enabled = enabled, modifier = Modifier.semantics { contentDescription = renameLabel }) {
                    Icon(Icons.Outlined.Edit, contentDescription = null)
                }
                IconButton(onClick = onDelete, enabled = enabled, modifier = Modifier.semantics { contentDescription = deleteLabel }) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = if (enabled) scheme.error else scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EqUserPresetNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    val valid = EchoEqualizerUserPresets.normalizeName(name) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(EchoEqualizerUserPresets.MaxNameLength) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.eq_user_preset_name)) },
                shape = RoundedCornerShape(16.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { EchoEqualizerUserPresets.normalizeName(name)?.let(onConfirm) }, enabled = valid) {
                Text(stringResource(R.string.eq_user_preset_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.error_log_clear_cancel)) }
        },
    )
}

@Composable
internal fun EqShareCodeDialog(
    preset: EchoEqualizerUserPreset,
    curve: List<EchoEqResponsePoint> = emptyList(),
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val code = remember(preset) { EchoEqualizerShareCodec.encode(preset) }
    val apo = remember(preset) { EchoEqualizerApoCodec.encode(preset) }
    var copied by remember { mutableStateOf<String?>(null) }
    val bandCount = if (preset.parametric) preset.filters.size else preset.gainsDb.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.eq_share_code)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SignalNote(stringResource(R.string.eq_share_preview, preset.name, bandCount, formatEqGain(preset.preampDb)))
                if (curve.size > 1) {
                    SignalEqPlot(
                        points = curve,
                        modifier = Modifier.fillMaxWidth().height(96.dp),
                        live = true,
                        showFrequencyLabels = false,
                    )
                }
                SignalNote(stringResource(R.string.eq_share_detail))
                if (code == null) {
                    SignalNote(stringResource(R.string.eq_share_import_invalid), error = true)
                } else {
                    SelectionContainer {
                        Text(code, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        onClick = { copied = if (copyEqShareCode(context, preset.name, code)) "code" else null },
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) { Text(stringResource(R.string.eq_share_copy)) }
                    if (apo != null) {
                        TextButton(
                            onClick = {
                                copyPlain(context, preset.name, apo)
                                copied = "apo"
                            },
                            contentPadding = PaddingValues(horizontal = 0.dp),
                        ) { Text(stringResource(R.string.eq_share_copy_apo)) }
                    }
                    TextButton(
                        onClick = { shareEqShareCode(context, preset.name, code) },
                        contentPadding = PaddingValues(horizontal = 0.dp),
                    ) { Text(stringResource(R.string.eq_share)) }
                    if (copied == "code") SignalNote(stringResource(R.string.eq_share_copied))
                    if (copied == "apo") SignalNote(stringResource(R.string.eq_share_apo_copied))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.error_log_clear_cancel)) }
        },
    )
}

@Composable
private fun EqImportShareCodeDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    val context = LocalContext.current
    var raw by remember { mutableStateOf("") }
    val parsed = remember(raw) { EchoEqualizerShareCodec.decode(raw, "import") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.eq_share_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                    label = { Text(stringResource(R.string.eq_share_import_hint)) },
                    minLines = 3,
                )
                TextButton(
                    onClick = { pasteEqShareCode(context)?.let { raw = it } },
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) { Text(stringResource(R.string.eq_share_paste)) }
                if (raw.isNotBlank() && parsed == null) {
                    SignalNote(stringResource(R.string.eq_share_import_invalid), error = true)
                } else {
                    parsed?.let { preset ->
                        val bandCount = if (preset.parametric) preset.filters.size else preset.gainsDb.size
                        SignalNote(stringResource(R.string.eq_share_preview, preset.name, bandCount, formatEqGain(preset.preampDb)))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let { onImport(raw) } }, enabled = parsed != null) {
                Text(stringResource(R.string.eq_share_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.error_log_clear_cancel)) }
        },
    )
}

private fun copyEqShareCode(context: Context, name: String, code: String): Boolean {
    copyPlain(context, name, "$name\n$code")
    return true
}

private fun copyPlain(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun shareEqShareCode(context: Context, name: String, code: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, name)
        putExtra(Intent.EXTRA_TEXT, "$name\n$code")
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.eq_share)))
    }
}

private fun pasteEqShareCode(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: return null
    return EchoEqualizerShareCodec.extract(text) ?: text.trim().takeIf { it.isNotEmpty() }
}

private sealed class EqUserPresetEditor {
    abstract val name: String
    data class Save(override val name: String) : EqUserPresetEditor()
    data class Rename(val id: String, override val name: String) : EqUserPresetEditor()
}
