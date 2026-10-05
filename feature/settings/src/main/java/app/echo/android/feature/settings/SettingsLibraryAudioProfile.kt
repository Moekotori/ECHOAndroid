package app.echo.android.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.LibraryAudioProfile
import java.text.NumberFormat

@Composable
internal fun SettingsLibraryAudioProfile(
    profile: LibraryAudioProfile?,
    isScanning: Boolean,
    failed: Boolean,
    onRefresh: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val percentages = remember(locale) { NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 0 } }
    val rates = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 3 } }
    LibraryStatsSection(stringResource(R.string.settings_library_audio_title), action = {
        TextButton(onClick = onRefresh, enabled = !isScanning) {
            Text(stringResource(R.string.settings_library_health_refresh))
        }
    }) {
        when {
            isScanning -> LibraryStatsNote(stringResource(R.string.settings_library_health_scanning))
            failed -> LibraryStatsNote(stringResource(R.string.settings_library_health_error))
            profile == null -> LibraryStatsNote(stringResource(R.string.settings_library_health_loading))
            profile.trackCount == 0 -> LibraryStatsNote(stringResource(R.string.settings_library_audio_empty))
        }
        if (profile != null && !isScanning && !failed && profile.trackCount > 0) {
            val categories = remember(profile, scheme) {
                listOf(
                    Triple(R.string.settings_library_audio_lossless, profile.losslessCount, scheme.primary),
                    Triple(R.string.settings_library_audio_lossy, profile.lossyCount, scheme.onSurfaceVariant),
                    Triple(R.string.settings_library_audio_dsd, profile.dsdCount, scheme.outline),
                    Triple(R.string.settings_library_audio_unconfirmed, profile.unconfirmedCount, scheme.outlineVariant),
                )
            }
            // A single small chart; labels, counts and shares remain neutral and readable.
            Canvas(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(5.dp).clip(CircleShape)) {
                var left = 0f
                categories.forEach { (_, count, color) ->
                    val width = size.width * count.coerceAtLeast(0).toFloat() / profile.trackCount
                    if (width > 0f) drawRect(color, Offset(left, 0f), Size(width, size.height))
                    left += width
                }
            }
            Column {
                categories.forEach { (label, count, _) ->
                    AudioQualityRow(stringResource(label), numbers.format(count),
                        percentages.format(count.toDouble() / profile.trackCount))
                }
            }
            LibraryStatsDivider()
            LibraryStatsDisclosure(stringResource(R.string.settings_library_audio_details)) {
                AudioSubheading(stringResource(R.string.settings_library_audio_formats))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    profile.formats.forEach { bucket ->
                        AudioDistributionChip(if (bucket.key == "UNKNOWN") stringResource(R.string.settings_library_audio_unknown)
                            else bucket.key, numbers.format(bucket.count))
                    }
                }
                AudioSubheading(stringResource(R.string.settings_library_audio_rates))
                LibraryStatsNote(stringResource(R.string.settings_library_audio_high_rate, numbers.format(profile.highSampleRateCount)))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    profile.sampleRates.forEach { bucket ->
                        val hz = bucket.key.toIntOrNull()
                        val label = when (bucket.key) {
                            "UNKNOWN" -> stringResource(R.string.settings_library_audio_unknown)
                            "OTHER" -> stringResource(R.string.settings_library_audio_other_rate)
                            "DSD" -> stringResource(R.string.settings_library_audio_dsd_rate)
                            else -> stringResource(R.string.settings_library_audio_khz, rates.format((hz ?: 0) / 1000.0))
                        }
                        AudioDistributionChip(label, numbers.format(bucket.count))
                    }
                }
                LibraryStatsNote(stringResource(R.string.settings_library_audio_scope))
                AudioMetadataAvailability(R.string.settings_library_audio_depth)
                AudioMetadataAvailability(R.string.settings_library_audio_bitrate)
                LibraryStatsNote(stringResource(R.string.settings_library_audio_depth_bitrate))
            }
        }
    }
}

@Composable
private fun AudioQualityRow(label: String, count: String, share: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Text(count, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
        Text(share, Modifier.widthIn(min = 48.dp), textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AudioDistributionChip(label: String, count: String) {
    Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(6.dp))
        .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface)
        Text(count, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AudioSubheading(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun AudioMetadataAvailability(label: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.settings_library_audio_not_recorded), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
