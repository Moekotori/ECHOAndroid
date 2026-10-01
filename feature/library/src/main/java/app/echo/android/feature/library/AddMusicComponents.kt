package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoSwitch
import app.echo.android.design.echoClickable

@Composable
internal fun AddMusicSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 12.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun AddMusicActionRow(
    icon: ImageVector, title: String, hint: String?, enabled: Boolean,
    value: String? = null,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
        .echoClickable(enabled = enabled, onClick = onClick)
        .heightIn(min = 56.dp).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        EchoIcon(icon, null, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f))
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (value != null) Text(value, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        EchoIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun AddMusicToggle(title: String, hint: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        EchoSwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
internal fun AddMusicFolderRow(folder: AddMusicFolder, enabled: Boolean, onRescan: () -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val name = folder.path.substringAfterLast('/').substringAfterLast(':').ifBlank { folder.path }
    val path = folder.path.substringAfter(':', folder.path)
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        EchoIcon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(path, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(folder.lastScanLabel?.let { stringResource(R.string.add_music_last_scan, it) }
                ?: stringResource(R.string.add_music_not_scanned), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(enabled = enabled, onClick = { menuOpen = true }) {
                EchoIcon(Icons.Rounded.MoreVert, stringResource(R.string.add_music_folder_actions, name))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_music_rescan)) },
                    leadingIcon = { EchoIcon(Icons.Rounded.Refresh, null, modifier = Modifier.size(20.dp)) },
                    enabled = enabled, onClick = { menuOpen = false; onRescan() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_music_remove)) },
                    leadingIcon = { EchoIcon(Icons.Rounded.Close, null, modifier = Modifier.size(20.dp)) },
                    enabled = enabled, onClick = { menuOpen = false; onRemove() },
                )
            }
        }
    }
}
