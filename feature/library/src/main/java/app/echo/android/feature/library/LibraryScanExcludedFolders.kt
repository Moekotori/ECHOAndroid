package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

internal const val MaxExcludedScanFolders = 64

@Composable
internal fun LibraryScanExcludedFolders(
    paths: Set<String>,
    enabled: Boolean,
    onChooseFolder: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val sortedPaths = remember(paths) { paths.sortedWith(String.CASE_INSENSITIVE_ORDER) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EchoIcon(Icons.Rounded.FolderOpen, null, Modifier.size(22.dp))
                Text(stringResource(R.string.scan_rules_excluded_folders_title), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (paths.isNotEmpty()) AddMusicBadge(paths.size.toString())
            }
            Text(stringResource(R.string.scan_rules_excluded_folders_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            sortedPaths.forEach { path -> key(path) {
                val name = path.trimEnd('/').substringAfterLast('/')
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(excludedFolderDisplayPath(path), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(enabled = enabled, onClick = { onRemove(path) }) {
                        EchoIcon(Icons.Rounded.Close, stringResource(R.string.scan_rules_remove_excluded_folder, name))
                    }
                }
            } }
            if (paths.size >= MaxExcludedScanFolders) Text(
                stringResource(R.string.scan_rules_excluded_folders_limit, MaxExcludedScanFolders),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(enabled = enabled && paths.size < MaxExcludedScanFolders,
                onClick = onChooseFolder, shape = MaterialTheme.shapes.medium,
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.scan_rules_add_excluded_folder))
            }
        }
    }
}

@Composable
private fun excludedFolderDisplayPath(path: String): String {
    val removablePrefix = "Removable/"
    return if (path.startsWith(removablePrefix, ignoreCase = true)) {
        val volumeAndPath = path.substring(removablePrefix.length)
        stringResource(R.string.scan_rules_external_folder_path, volumeAndPath.substringBefore('/'),
            volumeAndPath.substringAfter('/', ""))
    } else stringResource(R.string.scan_rules_phone_folder_path, path)
}
