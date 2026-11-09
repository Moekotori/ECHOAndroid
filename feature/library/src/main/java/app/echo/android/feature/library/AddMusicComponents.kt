package app.echo.android.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoSwitch
import app.echo.android.design.echoClickable

internal val AddMusicContentWidth = 680.dp

@Composable
internal fun AddMusicPageHeader(
    title: String,
    backLabel: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.widthIn(max = AddMusicContentWidth).fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, backLabel)
        }
        Text(
            title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        trailing?.invoke()
    }
}

@Composable
internal fun AddMusicSectionTitle(text: String, count: Int? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold)
        count?.let { AddMusicBadge(it.toString()) }
    }
}

@Composable
internal fun AddMusicBadge(text: String, highlighted: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = CircleShape,
        color = if (highlighted) colors.primary.copy(alpha = 0.12f).compositeOver(colors.surfaceContainerLow)
            else colors.surfaceContainerHighest,
    ) {
        Text(
            text, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
        )
    }
}

@Composable
internal fun AddMusicFolderAction(enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.extraLarge
    Surface(
        shape = shape,
        color = colors.primary.copy(alpha = 0.09f).compositeOver(colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.18f)),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(shape).echoClickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(width = 50.dp, height = 48.dp)) {
                Box(
                    Modifier.padding(start = 3.dp).size(width = 23.dp, height = 13.dp)
                        .background(colors.primary.copy(alpha = 0.16f), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                )
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(40.dp)
                        .background(colors.primary.copy(alpha = 0.16f), MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    EchoIcon(Icons.Rounded.CreateNewFolder, null, Modifier.size(23.dp),
                        tint = colors.onSurface.copy(alpha = if (enabled) 1f else 0.45f))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.scan_folder_add_title), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, color = colors.onSurface.copy(alpha = if (enabled) 1f else 0.45f))
                Text(stringResource(R.string.scan_folder_add_hint), style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
            }
            EchoIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun AddMusicActionRow(
    icon: ImageVector, title: String, hint: String?, enabled: Boolean,
    value: String? = null,
    trailingIcon: ImageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
        .echoClickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        EchoIcon(icon, null, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f))
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (value != null) Text(value, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        EchoIcon(trailingIcon, null, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun AddMusicToggle(
    title: String, hint: String?, checked: Boolean, enabled: Boolean,
    modifier: Modifier = Modifier, onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .then(modifier).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
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
internal fun AddMusicFolderRow(
    folder: AddMusicFolder, enabled: Boolean, watching: Boolean,
    onEdit: () -> Unit, onRescan: () -> Unit, onRemove: () -> Unit,
) {
    val name = folder.path.substringAfterLast('/').substringAfterLast(':').ifBlank { folder.path }
    val path = folder.path.substringAfter(':', folder.path)
    val editLabel = stringResource(R.string.scan_folder_edit_title)
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).echoClickable(enabled = enabled, role = Role.Button, onClickLabel = editLabel, onClick = onEdit)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EchoIcon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(22.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (path != name) Text(path, style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val watchLabel = stringResource(if (watching) R.string.scan_folder_watching else R.string.scan_folder_manual)
                    val scanLabel = folder.lastScanLabel?.let { stringResource(R.string.add_music_last_scan, it) }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        AddMusicBadge(watchLabel, highlighted = watching)
                        if (scanLabel != null) Text(scanLabel, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            AddMusicFolderMenu(name, enabled, onEdit, onRescan, onRemove)
        }
    }
}

@Composable
private fun AddMusicFolderMenu(
    name: String, enabled: Boolean,
    onEdit: () -> Unit, onRescan: () -> Unit, onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(enabled = enabled, onClick = { menuOpen = true }) {
            EchoIcon(Icons.Rounded.MoreVert, stringResource(R.string.add_music_folder_actions, name))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.scan_folder_edit_title)) },
                leadingIcon = { EchoIcon(Icons.Rounded.Tune, null, modifier = Modifier.size(20.dp)) },
                enabled = enabled, onClick = { menuOpen = false; onEdit() },
            )
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
