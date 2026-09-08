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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.BorderStroke
import app.echo.android.design.echoClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
    var filtersEnabled by rememberSaveable { mutableStateOf(true) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val options = if (filtersEnabled) {
        LibraryScanOptions(minDuration, minSize, excludeNonMusic, excludeHidden)
    } else {
        LibraryScanOptions(0L, 0L, false, false)
    }
    val colors = rememberScanGlassColors()
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = colors.surface.copy(alpha = 1f),
            border = BorderStroke(1.dp, colors.border),
        ) {
            Column(Modifier.heightIn(max = 640.dp).padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(L10nR.string.library_add_music), color = colors.content,
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(stringResource(L10nR.string.scan_simple_hint), color = colors.muted,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, stringResource(L10nR.string.feature_library_close_473a69), tint = colors.muted)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    Row(
                        Modifier.fillMaxWidth().toggleable(filtersEnabled, role = Role.Switch, onValueChange = { filtersEnabled = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(L10nR.string.scan_enable_filters), color = colors.content,
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(L10nR.string.scan_filters_hint), color = colors.muted,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = filtersEnabled, onCheckedChange = null)
                    }
                    if (filtersEnabled) {
                        TextButton(onClick = { showDetails = !showDetails }) {
                            Text(stringResource(if (showDetails) L10nR.string.scan_hide_details else L10nR.string.scan_edit_filters))
                            Spacer(Modifier.width(4.dp))
                            Icon(if (showDetails) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                    AnimatedVisibility(visible = filtersEnabled && showDetails) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(color = colors.border)
                            Text(stringResource(L10nR.string.scan_min_duration), color = colors.muted,
                                style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0L to L10nR.string.scan_any, 30_000L to L10nR.string.scan_30_seconds, 60_000L to L10nR.string.scan_60_seconds).forEach { (value, label) ->
                                    FilterChip(modifier = Modifier.weight(1f), selected = minDuration == value,
                                        onClick = { minDuration = value }, label = { Text(stringResource(label), maxLines = 1) })
                                }
                            }
                            Text(stringResource(L10nR.string.scan_min_size), color = colors.muted,
                                style = MaterialTheme.typography.labelMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0L to L10nR.string.scan_any, 102_400L to L10nR.string.scan_100_kb, 1_048_576L to L10nR.string.scan_1_mb).forEach { (value, label) ->
                                    FilterChip(modifier = Modifier.weight(1f), selected = minSize == value,
                                        onClick = { minSize = value }, label = { Text(stringResource(label), maxLines = 1) })
                                }
                            }
                            ScanFilterToggle(stringResource(L10nR.string.scan_skip_non_music), excludeNonMusic) { excludeNonMusic = it }
                            ScanFilterToggle(stringResource(L10nR.string.scan_skip_hidden), excludeHidden) { excludeHidden = it }
                            Text(stringResource(L10nR.string.scan_filter_hint), color = colors.muted, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { onScanFolder(options) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = echoAccentColor(), contentColor = MaterialTheme.colorScheme.onPrimary),
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(L10nR.string.scan_choose_folder), fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = { onScanAll(options) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(L10nR.string.scan_device_instead), color = colors.muted)
                }
            }
        }
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
