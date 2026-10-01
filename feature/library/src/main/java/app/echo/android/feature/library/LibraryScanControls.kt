package app.echo.android.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.LibraryScanOptions

private val LosslessScanExtensions = setOf("flac", "wav", "aiff", "aif", "aifc", "dsf", "dff")

@Composable
internal fun libraryScanPresetLabel(options: LibraryScanOptions): String {
    val defaults = LibraryScanOptions()
    val preset = options.copy(excludedRelativePaths = emptySet())
    return stringResource(when (preset) {
        defaults -> R.string.add_music_preset_recommended
        LibraryScanOptions(0L, 0L, false, false) -> R.string.add_music_preset_all
        defaults.copy(allowedExtensions = LosslessScanExtensions) -> R.string.add_music_preset_lossless
        else -> R.string.add_music_preset_custom
    })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryScanFilters(options: LibraryScanOptions, enabled: Boolean, onChange: (LibraryScanOptions) -> Unit) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    var excludedPaths by rememberSaveable { mutableStateOf(options.excludedRelativePaths.joinToString("\n")) }
    LaunchedEffect(options.excludedRelativePaths) {
        val currentPaths = excludedPaths.lineSequence().map(String::trim).filter(String::isNotEmpty).toSet()
        if (currentPaths != options.excludedRelativePaths) excludedPaths = options.excludedRelativePaths.joinToString("\n")
    }
    val recommended = LibraryScanOptions(excludedRelativePaths = options.excludedRelativePaths)
    val all = LibraryScanOptions(0L, 0L, false, false, options.excludedRelativePaths)
    val lossless = recommended.copy(allowedExtensions = LosslessScanExtensions)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(recommended to R.string.add_music_preset_recommended, all to R.string.add_music_preset_all,
                lossless to R.string.add_music_preset_lossless).forEach { (preset, label) ->
                FilterChip(selected = options == preset, enabled = enabled, onClick = { onChange(preset) },
                    label = { Text(stringResource(label)) })
            }
        }
        Text(stringResource(R.string.scan_min_duration), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        ScanChoices(options.minDurationMs, enabled,
            listOf(0L to R.string.scan_any, 30_000L to R.string.scan_30_seconds, 60_000L to R.string.scan_60_seconds)) {
            onChange(options.copy(minDurationMs = it))
        }
        Text(stringResource(R.string.scan_min_size), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        ScanChoices(options.minSizeBytes, enabled,
            listOf(0L to R.string.scan_any, 102_400L to R.string.scan_100_kb, 1_048_576L to R.string.scan_1_mb)) {
            onChange(options.copy(minSizeBytes = it))
        }
        AddMusicToggle(stringResource(R.string.scan_skip_non_music), null,
            options.excludeNonMusicFolders, enabled) { onChange(options.copy(excludeNonMusicFolders = it)) }
        AddMusicToggle(stringResource(R.string.scan_skip_hidden), null,
            options.excludeHiddenFolders, enabled) { onChange(options.copy(excludeHiddenFolders = it)) }
        TextButton(onClick = { advanced = !advanced }) {
            Text(stringResource(if (advanced) R.string.add_music_hide_advanced else R.string.scan_advanced_options))
        }
        if (advanced) {
            LibraryScanFormatOptions(options.allowedExtensions, enabled = enabled) { onChange(options.copy(allowedExtensions = it)) }
            OutlinedTextField(
                value = excludedPaths, enabled = enabled,
                onValueChange = { text ->
                    excludedPaths = text
                    onChange(options.copy(excludedRelativePaths = text.lineSequence().map(String::trim)
                        .filter(String::isNotEmpty).toSet()))
                },
                label = { Text(stringResource(R.string.scan_excluded_folders)) },
                supportingText = { Text(stringResource(R.string.scan_excluded_folders_hint)) },
                modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4,
            )
            Text(stringResource(R.string.scan_filter_hint), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScanChoices(selected: Long, enabled: Boolean, choices: List<Pair<Long, Int>>, onSelect: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (value, label) ->
            FilterChip(selected = selected == value, enabled = enabled, onClick = { onSelect(value) },
                label = { Text(stringResource(label)) }, modifier = Modifier.weight(1f))
        }
    }
}
