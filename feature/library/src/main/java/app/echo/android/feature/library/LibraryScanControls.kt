package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.library.LibraryScanOptions

private val LosslessScanExtensions = setOf("flac", "ape", "wv", "tta", "tak", "wav", "w64", "alac", "aiff", "aif", "aifc", "dsf", "dff")
private val DurationScanChoices = listOf(0L to R.string.scan_any, 30_000L to R.string.scan_30_seconds, 60_000L to R.string.scan_60_seconds)
private val SizeScanChoices = listOf(0L to R.string.scan_any, 102_400L to R.string.scan_100_kb, 1_048_576L to R.string.scan_1_mb)

private data class ScanPreset(val options: LibraryScanOptions, val title: Int, val hint: Int)

private val ScanPresets = listOf(
    ScanPreset(LibraryScanOptions(), R.string.add_music_preset_recommended, R.string.scan_rules_recommended_hint),
    ScanPreset(LibraryScanOptions(0L, 0L, false, false), R.string.add_music_preset_all, R.string.scan_rules_all_hint),
    ScanPreset(LibraryScanOptions(allowedExtensions = LosslessScanExtensions),
        R.string.add_music_preset_lossless, R.string.scan_rules_lossless_hint),
)

@Composable
internal fun libraryScanPresetLabel(options: LibraryScanOptions): String = stringResource(
    ScanPresets.firstOrNull { it.options == options.copy(excludedRelativePaths = emptySet()) }?.title
        ?: R.string.add_music_preset_custom,
)

@Composable
internal fun LibraryScanFilters(
    options: LibraryScanOptions,
    enabled: Boolean,
    onChooseExcludedFolder: () -> Unit,
    onChange: (LibraryScanOptions) -> Unit,
) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScanRulesHeading(stringResource(R.string.scan_rules_folder_filters))
            AddMusicToggle(stringResource(R.string.scan_rules_non_music), stringResource(R.string.scan_rules_non_music_hint),
                options.excludeNonMusicFolders, enabled) { onChange(options.copy(excludeNonMusicFolders = it)) }
            AddMusicToggle(stringResource(R.string.scan_skip_hidden), stringResource(R.string.scan_rules_hidden_hint),
                options.excludeHiddenFolders, enabled) { onChange(options.copy(excludeHiddenFolders = it)) }
            Text(stringResource(R.string.scan_rules_folder_filters_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LibraryScanExcludedFolders(options.excludedRelativePaths, enabled, onChooseExcludedFolder) { path ->
            onChange(options.copy(excludedRelativePaths = options.excludedRelativePaths - path))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
                onClick = { advanced = !advanced },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(if (advanced) R.string.add_music_hide_advanced else R.string.scan_advanced_options),
                            style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.scan_rules_advanced_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            stringResource(R.string.scan_rules_advanced_summary,
                                scanChoiceLabel(options.minDurationMs, DurationScanChoices),
                                scanChoiceLabel(options.minSizeBytes, SizeScanChoices),
                                stringResource(if (options.allowedExtensions.isEmpty()) R.string.scan_all_formats
                                    else R.string.scan_rules_selected_formats)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    EchoIcon(if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                }
            }
            if (advanced) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ScanRulesHeading(stringResource(R.string.scan_rules_presets), Modifier.weight(1f))
                        Text(libraryScanPresetLabel(options), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.fillMaxWidth().selectableGroup()) {
                            ScanPresets.forEachIndexed { index, preset ->
                                val selected = options.copy(excludedRelativePaths = emptySet()) == preset.options
                                Surface(color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface) {
                                    Row(
                                        Modifier.fillMaxWidth().selectable(selected, enabled = enabled, role = Role.RadioButton,
                                            onClick = { onChange(preset.options.copy(excludedRelativePaths = options.excludedRelativePaths)) })
                                            .heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(stringResource(preset.title), style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium)
                                            Text(stringResource(preset.hint), style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        RadioButton(selected = selected, onClick = null, enabled = enabled)
                                    }
                                }
                                if (index < ScanPresets.lastIndex) HorizontalDivider(
                                    Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ScanRulesHeading(stringResource(R.string.scan_rules_file_filters))
                    ScanChoices(stringResource(R.string.scan_min_duration), options.minDurationMs, enabled,
                        DurationScanChoices) {
                        onChange(options.copy(minDurationMs = it))
                    }
                    ScanChoices(stringResource(R.string.scan_min_size), options.minSizeBytes, enabled,
                        SizeScanChoices) {
                        onChange(options.copy(minSizeBytes = it))
                    }
                    Text(stringResource(R.string.scan_rules_file_filters_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LibraryScanFormatOptions(options.allowedExtensions, enabled = enabled) {
                    onChange(options.copy(allowedExtensions = it))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun scanChoiceLabel(value: Long, choices: List<Pair<Long, Int>>): String =
    stringResource(choices.firstOrNull { it.first == value }?.second ?: R.string.add_music_preset_custom)

@Composable
private fun ScanRulesHeading(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanChoices(title: String, selected: Long, enabled: Boolean, choices: List<Pair<Long, Int>>, onSelect: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            choices.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = selected == value,
                    enabled = enabled,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, choices.size, MaterialTheme.shapes.medium),
                    icon = {},
                ) { Text(stringResource(label)) }
            }
        }
    }
}
