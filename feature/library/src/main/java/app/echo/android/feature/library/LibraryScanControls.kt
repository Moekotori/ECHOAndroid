package app.echo.android.feature.library

import app.echo.android.model.library.LibraryScanOptions
import app.echo.android.feature.library.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import app.echo.android.design.echoClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Scanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.echo.android.design.EchoAccentDeep
import app.echo.android.design.EchoColors
import app.echo.android.design.EchoDarkGlassBorder
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoGlassPanel
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.echoAccentColor
import app.echo.android.design.RoonInk
import app.echo.android.design.RoonMuted
import app.echo.android.model.library.LibraryScanProgress

private data class ScanGlassColors(
    val surface: Color,
    val optionSurface: Color,
    val border: Color,
    val content: Color,
    val muted: Color,
)

@Composable
private fun rememberScanGlassColors(): ScanGlassColors {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    return remember(scheme, dark) {
        ScanGlassColors(
            surface = if (dark) EchoGlassPanel.copy(alpha = 0.54f) else Color.White.copy(alpha = 0.96f),
            optionSurface = if (dark) EchoGlassPanel.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.74f),
            border = if (dark) EchoDarkGlassBorder else EchoGlassBorder,
            content = if (dark) scheme.onSurface else RoonInk,
            muted = if (dark) scheme.onSurfaceVariant.copy(alpha = 0.90f) else RoonMuted,
        )
    }
}

@Composable
internal fun LibraryScanAction(
    hasPermission: Boolean,
    scanState: LibraryScanProgress,
    onRequestPermission: () -> Unit,
    onScanFolder: (LibraryScanOptions) -> Unit,
    onScanAll: (LibraryScanOptions) -> Unit,
    onCancelScan: () -> Unit,
) {
    var showScanOptions by remember { mutableStateOf(false) }
    val colors = rememberScanGlassColors()
    val description = when {
        scanState.isScanning -> stringResource(L10nR.string.feature_library_cancel_library_scan_4033b9)
        else -> stringResource(L10nR.string.feature_library_scan_library_3d1814)
    }
    val label = when {
        scanState.isScanning -> stringResource(L10nR.string.feature_library_stop_739d16)
        else -> stringResource(L10nR.string.feature_library_scan_fe69d2)
    }
    val accent = when {
        scanState.error != null -> EchoColors.Coral
        else -> colors.content
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .echoClickable(
                onClick = when {
                    scanState.isScanning -> onCancelScan
                    else -> {
                        { showScanOptions = true }
                    }
                },
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.LibraryMusic,
            contentDescription = description,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            color = accent,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }

    if (showScanOptions) {
        LibraryScanOptionsDialog(
            onDismiss = { showScanOptions = false },
            onScanFolder = { options ->
                showScanOptions = false
                onScanFolder(options)
            },
            onScanAll = { options ->
                showScanOptions = false
                onScanAll(options)
            },
        )
    }
}

@Composable
internal fun LibraryScanOptionsDialog(
    onDismiss: () -> Unit,
    onScanFolder: (LibraryScanOptions) -> Unit,
    onScanAll: (LibraryScanOptions) -> Unit,
) {
    var minDuration by rememberSaveable { mutableStateOf(30_000L) }
    var minSize by rememberSaveable { mutableStateOf(100L * 1024L) }
    var excludeNonMusic by rememberSaveable { mutableStateOf(true) }
    var excludeHidden by rememberSaveable { mutableStateOf(true) }
    val options = LibraryScanOptions(minDuration, minSize, excludeNonMusic, excludeHidden)
    val colors = rememberScanGlassColors()
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.border),
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(L10nR.string.feature_library_scan_library_3d1814),
                            color = colors.content,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(L10nR.string.scan_scope_hint),
                            color = colors.muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(L10nR.string.feature_library_close_473a69),
                            tint = colors.muted,
                        )
                    }
                }

                Text(stringResource(L10nR.string.scan_min_duration), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0L to L10nR.string.scan_any, 30_000L to L10nR.string.scan_30_seconds, 60_000L to L10nR.string.scan_60_seconds).forEach { (value, label) ->
                        FilterChip(selected = minDuration == value, onClick = { minDuration = value }, label = { Text(stringResource(label)) })
                    }
                }
                Text(stringResource(L10nR.string.scan_min_size), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0L to L10nR.string.scan_any, 102_400L to L10nR.string.scan_100_kb, 1_048_576L to L10nR.string.scan_1_mb).forEach { (value, label) ->
                        FilterChip(selected = minSize == value, onClick = { minSize = value }, label = { Text(stringResource(label)) })
                    }
                }
                ScanFilterToggle(stringResource(L10nR.string.scan_skip_non_music), excludeNonMusic) { excludeNonMusic = it }
                ScanFilterToggle(stringResource(L10nR.string.scan_skip_hidden), excludeHidden) { excludeHidden = it }
                Text(stringResource(L10nR.string.scan_filter_hint), color = colors.muted, style = MaterialTheme.typography.bodySmall)

                LibraryScanOption(
                    icon = Icons.Rounded.FolderOpen,
                    title = stringResource(L10nR.string.scan_choose_folder),
                    subtitle = stringResource(L10nR.string.scan_folder_hint),
                    onClick = { onScanFolder(options) },
                    accent = echoAccentColor(),
                )
                LibraryScanOption(
                    icon = Icons.Rounded.LibraryMusic,
                    title = stringResource(L10nR.string.scan_device),
                    subtitle = stringResource(L10nR.string.scan_device_hint),
                    onClick = { onScanAll(options) },
                    accent = EchoAccentDeep,
                )
            }
        }
    }
}

@Composable
private fun LibraryScanOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    accent: Color,
) {
    val colors = rememberScanGlassColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.16f)), RoundedCornerShape(18.dp))
            .echoClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(14.dp),
            color = colors.optionSurface,
            border = BorderStroke(1.dp, colors.border),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                color = colors.content,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = colors.muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(2.dp))
        Icon(Icons.Rounded.Scanner, contentDescription = null, tint = echoAccentColor(), modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun ScanFilterToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}
