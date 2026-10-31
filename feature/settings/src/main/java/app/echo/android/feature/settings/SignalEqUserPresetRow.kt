package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.echoClickable
import app.echo.android.model.playback.EchoEqualizerUserPreset

/** Applying sound and managing a saved preset have separate availability. */
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
    applyEnabled: Boolean = enabled,
) {
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember(preset.id) { mutableStateOf(false) }
    var deleting by rememberSaveable(preset.id) { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) { menuOpen = false; deleting = false } }
    val renameLabel = stringResource(R.string.eq_user_preset_rename)
    val deleteLabel = stringResource(R.string.eq_user_preset_delete)
    val shareLabel = stringResource(R.string.eq_share)
    val actionsLabel = stringResource(R.string.signal_preset_actions, preset.name)
    val detail = if (preset.parametric) stringResource(R.string.eq_user_preset_bands, preset.filters.size)
        else eqPresetLabel(preset.graphicPresetId)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            Modifier.weight(1f).heightIn(min = 48.dp)
                .echoClickable(enabled = enabled && applyEnabled, role = Role.Button, onClick = onApply)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(preset.name, style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = if (active) scheme.primary else scheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            val source = preset.sourceLabel
            SignalNote(if (preset.fromOpra && source != null) source else detail)
        }
        if (showActions) {
            if (showEditDelete) Box {
                IconButton(onClick = { menuOpen = true }, enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = actionsLabel }) {
                    EchoIcon(Icons.Rounded.MoreVert, null)
                }
                DropdownMenu(expanded = menuOpen && enabled, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(shareLabel) }, leadingIcon = { EchoIcon(Icons.Outlined.Share, null) },
                        onClick = { menuOpen = false; onShare() })
                    DropdownMenuItem(text = { Text(renameLabel) }, leadingIcon = { EchoIcon(Icons.Outlined.Edit, null) },
                        onClick = { menuOpen = false; onRename() })
                    DropdownMenuItem(text = { Text(deleteLabel, color = scheme.error) },
                        leadingIcon = { EchoIcon(Icons.Outlined.Delete, null) },
                        onClick = { menuOpen = false; deleting = true })
                }
            } else {
                IconButton(onClick = onShare, enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = shareLabel }) {
                    EchoIcon(Icons.Outlined.Share, null)
                }
            }
        }
    }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text(deleteLabel) },
        text = { Text(stringResource(R.string.signal_delete_preset_confirm, preset.name)) },
        confirmButton = {
            TextButton(enabled = enabled, onClick = { deleting = false; onDelete() },
                colors = ButtonDefaults.textButtonColors(contentColor = scheme.error)) { Text(deleteLabel) }
        },
        dismissButton = {
            TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.error_log_clear_cancel)) }
        },
    )
}
