package app.echo.android.feature.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.NumberFormat

/** Existing aggregates only; this layout never reads music files. */
@Composable
internal fun SettingsLibraryOverview(
    trackCount: Int,
    albumCount: Int,
    artistCount: Int,
    durationMs: Long,
    localSizeBytes: Long,
    offlineSizeBytes: Long,
    onOpenLibrary: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val localBytes = localSizeBytes.coerceAtLeast(0L)
    val offlineBytes = offlineSizeBytes.coerceIn(0L, Long.MAX_VALUE - localBytes)
    val minutes = durationMs.coerceAtLeast(0L) / 60_000L

    LibraryStatsSection(stringResource(R.string.settings_library_overview_title), action = {
        TextButton(onClick = onOpenLibrary) { Text(stringResource(R.string.settings_library_overview_manage)) }
    }) {
        LibraryStatsPanel {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val compact = maxWidth < 280.dp || configuration.fontScale > 1.3f ||
                    maxOf(trackCount, albumCount, artistCount) >= 100_000
                if (compact) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LibraryOverviewDetail(stringResource(R.string.settings_library_overview_tracks),
                        numbers.format(trackCount.coerceAtLeast(0)))
                    LibraryOverviewDetail(stringResource(R.string.settings_albums),
                        numbers.format(albumCount.coerceAtLeast(0)))
                    LibraryOverviewDetail(stringResource(R.string.settings_artists),
                        numbers.format(artistCount.coerceAtLeast(0)))
                } else Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically) {
                LibraryOverviewMetric(numbers.format(trackCount.coerceAtLeast(0)),
                    stringResource(R.string.settings_library_overview_tracks), Modifier.weight(1f))
                LibraryOverviewMetric(numbers.format(albumCount.coerceAtLeast(0)),
                    stringResource(R.string.settings_albums), Modifier.weight(1f))
                LibraryOverviewMetric(numbers.format(artistCount.coerceAtLeast(0)),
                    stringResource(R.string.settings_artists), Modifier.weight(1f))
                }
            }
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LibraryStatsDivider()
                LibraryOverviewDetail(stringResource(R.string.settings_library_overview_storage),
                    Formatter.formatFileSize(context, localBytes + offlineBytes))
                LibraryOverviewDetail(stringResource(R.string.settings_library_overview_duration),
                    stringResource(R.string.settings_library_overview_hours_minutes,
                        numbers.format(minutes / 60L), numbers.format(minutes % 60L)))
            }
        }
        if (trackCount <= 0) LibraryStatsNote(stringResource(R.string.settings_library_overview_empty))
        LibraryStatsDisclosure(stringResource(R.string.settings_library_storage_details)) {
            LibraryOverviewDetail(stringResource(R.string.settings_library_overview_local),
                Formatter.formatFileSize(context, localBytes))
            LibraryOverviewDetail(stringResource(R.string.settings_library_overview_offline),
                Formatter.formatFileSize(context, offlineBytes))
            LibraryStatsNote(stringResource(R.string.settings_library_overview_scope))
        }
    }
}

@Composable
private fun LibraryOverviewMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LibraryOverviewDetail(label: String, value: String) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface)
    }
}
