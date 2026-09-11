package app.echo.android.feature.library

import app.echo.android.model.library.LibraryScanOptions
import app.echo.android.feature.library.R as L10nR
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.TextButton
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
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.echoAccentColor
import app.echo.android.design.echoTheme
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
    val theme = echoTheme()
    return remember(scheme, dark, theme) {
        ScanGlassColors(
            surface = if (dark) theme.panel.copy(alpha = 0.54f) else Color.White.copy(alpha = 0.96f),
            border = theme.glassBorder,
            content = if (dark) scheme.onSurface else theme.heading,
            muted = if (dark) scheme.onSurfaceVariant.copy(alpha = 0.90f) else theme.muted,
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
            color = if (LocalEchoDarkTheme.current) Color(0xFF191A1E) else Color(0xFFFAF9F7),
            border = BorderStroke(1.dp, colors.border),
        ) {
            Column(Modifier.heightIn(max = 600.dp).padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(L10nR.string.library_add_music), color = colors.content,
                            style = MaterialTheme.typography.titleLarge, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(L10nR.string.scan_simple_hint), color = colors.muted,
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, stringResource(L10nR.string.feature_library_close_473a69), tint = colors.muted)
                    }
                }
                Spacer(Modifier.height(20.dp))
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
                        ScanToggleIndicator(filtersEnabled)
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
                            ScanSegments(minDuration, listOf(0L to L10nR.string.scan_any, 30_000L to L10nR.string.scan_30_seconds, 60_000L to L10nR.string.scan_60_seconds)) { minDuration = it }
                            Text(stringResource(L10nR.string.scan_min_size), color = colors.muted,
                                style = MaterialTheme.typography.labelMedium)
                            ScanSegments(minSize, listOf(0L to L10nR.string.scan_any, 102_400L to L10nR.string.scan_100_kb, 1_048_576L to L10nR.string.scan_1_mb)) { minSize = it }
                            ScanFilterToggle(stringResource(L10nR.string.scan_skip_non_music), excludeNonMusic) { excludeNonMusic = it }
                            ScanFilterToggle(stringResource(L10nR.string.scan_skip_hidden), excludeHidden) { excludeHidden = it }
                            Text(stringResource(L10nR.string.scan_filter_hint), color = colors.muted, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(echoAccentColor().copy(alpha = 0.12f))
                        .border(1.dp, echoAccentColor().copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                        .echoClickable(onClick = { onScanFolder(options) })
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = echoAccentColor(), modifier = Modifier.size(23.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(L10nR.string.scan_choose_folder), modifier = Modifier.weight(1f),
                        color = colors.content, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = echoAccentColor(), modifier = Modifier.size(18.dp))
                }
                Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
                    .echoClickable(onClick = { onScanAll(options) }), contentAlignment = Alignment.Center) {
                    Text(stringResource(L10nR.string.scan_device_instead), color = colors.muted,
                        style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
private fun ScanFilterToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = rememberScanGlassColors()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = colors.content,
            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal)
        Spacer(Modifier.width(12.dp))
        ScanToggleIndicator(checked)
    }
}

@Composable
private fun ScanToggleIndicator(checked: Boolean) {
    val position by animateFloatAsState(if (checked) 1f else 0f, tween(160), label = "scan-toggle")
    val accent = echoAccentColor()
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    Canvas(Modifier.size(width = 34.dp, height = 20.dp)) {
        drawRoundRect(color = androidx.compose.ui.graphics.lerp(muted, accent.copy(alpha = 0.75f), position),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
        val radius = 7.dp.toPx()
        drawCircle(color = Color.White, radius = radius,
            center = Offset(10.dp.toPx() + position * 14.dp.toPx(), size.height / 2))
    }
}

@Composable
private fun ScanSegments(selected: Long, choices: List<Pair<Long, Int>>, onSelect: (Long) -> Unit) {
    val colors = rememberScanGlassColors()
    val accent = echoAccentColor()
    Row(Modifier.fillMaxWidth().selectableGroup().clip(RoundedCornerShape(10.dp))
        .background(colors.content.copy(alpha = 0.045f)).padding(3.dp)) {
        choices.forEach { (value, label) ->
            val active = selected == value
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (active) accent.copy(alpha = 0.16f) else Color.Transparent)
                    .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(value) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(label), color = if (active) accent else colors.muted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}
