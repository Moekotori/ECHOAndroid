package app.echo.android.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
    val palette = libraryPalette()
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val percentages = remember(locale) { NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 0 } }
    val rates = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 3 } }
    Column(Modifier.fillMaxWidth().padding(horizontal = SettingsContentInset),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_library_audio_title), Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            TextButton(onClick = onRefresh, enabled = !isScanning) {
                Text(stringResource(R.string.settings_library_health_refresh))
            }
        }
        when {
            isScanning -> AudioNote(stringResource(R.string.settings_library_health_scanning))
            failed -> AudioNote(stringResource(R.string.settings_library_health_error))
            profile == null -> AudioNote(stringResource(R.string.settings_library_health_loading))
            profile.trackCount == 0 -> AudioNote(stringResource(R.string.settings_library_audio_empty))
        }
        if (profile != null && !isScanning && !failed && profile.trackCount > 0) {
            val categories = listOf(
                Triple(R.string.settings_library_audio_lossless, profile.losslessCount, palette.teal),
                Triple(R.string.settings_library_audio_lossy, profile.lossyCount, palette.blue),
                Triple(R.string.settings_library_audio_dsd, profile.dsdCount, palette.violet),
                Triple(R.string.settings_library_audio_unconfirmed, profile.unconfirmedCount, palette.amber),
            )
            LibraryTintPanel(palette.blue) {
                Canvas(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)) {
                    var left = 0f
                    categories.forEach { (_, count, color) ->
                        val width = size.width * count.toFloat() / profile.trackCount
                        if (width > 0f) drawRect(color, Offset(left, 0f), Size(width, size.height))
                        left += width
                    }
                }
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (configuration.fontScale > 1.3f) {
                        categories.forEach { (label, count, color) ->
                            AudioQualityMetric(stringResource(label), numbers.format(count),
                                percentages.format(count.toDouble() / profile.trackCount), color, Modifier.fillMaxWidth())
                        }
                    } else {
                        categories.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                pair.forEach { (label, count, color) ->
                                    AudioQualityMetric(stringResource(label), numbers.format(count),
                                        percentages.format(count.toDouble() / profile.trackCount), color, Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            AudioSubheading(stringResource(R.string.settings_library_audio_formats))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profile.formats.forEach { bucket ->
                    val color = when (bucket.key) {
                        "FLAC", "ALAC", "APE" -> palette.teal
                        "MP2", "MP3", "AAC", "OPUS", "VORBIS", "AC3", "EAC3", "AMR" -> palette.blue
                        "DSD" -> palette.violet
                        else -> palette.amber
                    }
                    AudioDistributionChip(if (bucket.key == "UNKNOWN") stringResource(R.string.settings_library_audio_unknown)
                        else bucket.key, numbers.format(bucket.count), color)
                }
            }
            AudioSubheading(stringResource(R.string.settings_library_audio_rates))
            Text(stringResource(R.string.settings_library_audio_high_rate, numbers.format(profile.highSampleRateCount)),
                style = MaterialTheme.typography.bodySmall, color = palette.violet)
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
                    AudioDistributionChip(label, numbers.format(bucket.count),
                        if (hz != null && hz > 48000 || bucket.key == "DSD") palette.violet else palette.blue)
                }
            }
            AudioNote(stringResource(R.string.settings_library_audio_scope))
            LibraryTintPanel(palette.violet) {
                AudioMetadataAvailability(R.string.settings_library_audio_depth)
                AudioMetadataAvailability(R.string.settings_library_audio_bitrate)
            }
            AudioNote(stringResource(R.string.settings_library_audio_depth_bitrate))
        }
    }
}

@Composable
private fun AudioQualityMetric(label: String, count: String, share: String, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(count, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            Text(share, Modifier.padding(bottom = 3.dp), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AudioDistributionChip(label: String, count: String, color: Color) {
    Row(Modifier.background(color.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = color)
        Text(count, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AudioSubheading(text: String) {
    Text(text, Modifier.semantics { heading() }, style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun AudioMetadataAvailability(label: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Text(stringResource(R.string.settings_library_audio_not_recorded), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AudioNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
