package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoTextButton
import app.echo.android.design.echoClickable
import app.echo.android.model.playback.EchoEqualizerUserPreset
import app.echo.android.model.playback.EchoEqualizerUserPresets

@Composable
internal fun SignalEqUserPresets(
    presets: List<EchoEqualizerUserPreset>,
    activeId: String?,
    defaultSaveName: String,
    enabled: Boolean,
    onSave: (String) -> Unit,
    onApply: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var editor by remember { mutableStateOf<EqUserPresetEditor?>(null) }
    val canSave = presets.size < EchoEqualizerUserPresets.MaxCount
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.eq_user_presets),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            EchoTextButton(
                text = stringResource(R.string.eq_user_preset_save),
                onClick = { editor = EqUserPresetEditor.Save(defaultSaveName) },
                enabled = enabled && canSave,
            )
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
}

@Composable
internal fun SignalEqUserPresetRow(
    preset: EchoEqualizerUserPreset,
    active: Boolean,
    enabled: Boolean,
    onApply: () -> Unit,
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {},
    showActions: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val renameLabel = stringResource(R.string.eq_user_preset_rename)
    val deleteLabel = stringResource(R.string.eq_user_preset_delete)
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
            IconButton(onClick = onRename, enabled = enabled, modifier = Modifier.semantics { contentDescription = renameLabel }) {
                Icon(Icons.Outlined.Edit, contentDescription = null)
            }
            IconButton(onClick = onDelete, enabled = enabled, modifier = Modifier.semantics { contentDescription = deleteLabel }) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = if (enabled) scheme.error else scheme.onSurfaceVariant)
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

private sealed class EqUserPresetEditor {
    abstract val name: String
    data class Save(override val name: String) : EqUserPresetEditor()
    data class Rename(val id: String, override val name: String) : EqUserPresetEditor()
}
