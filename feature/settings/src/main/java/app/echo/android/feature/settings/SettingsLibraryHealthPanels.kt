package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.model.library.LibraryHealthStats
import app.echo.android.model.library.LibraryLyricsInspection
import java.text.DateFormat

@Composable
internal fun SettingsLibraryMetadataPanel(health: LibraryHealthStats?, isScanning: Boolean, failed: Boolean) {
    LibraryStatsSection(stringResource(R.string.settings_library_health_title)) {
        LibraryStatsDivider()
        when {
            isScanning -> LibraryStatsNote(stringResource(R.string.settings_library_health_scanning))
            failed -> LibraryStatsNote(stringResource(R.string.settings_library_health_error))
            health == null -> LibraryStatsNote(stringResource(R.string.settings_library_health_loading))
        }
        health?.let { snapshot ->
            HealthCount(R.string.settings_library_health_cover, snapshot.missingCoverCount)
            HealthCount(R.string.settings_library_health_album, snapshot.missingAlbumCount)
            HealthCount(R.string.settings_library_health_artist, snapshot.missingArtistCount)
            LibraryStatsDisclosure(stringResource(R.string.settings_library_metadata_details)) {
                HealthCount(R.string.settings_library_health_folders, snapshot.folderCount)
                HealthCount(R.string.settings_library_health_genre, snapshot.missingGenreCount)
                HealthCount(R.string.settings_library_health_year, snapshot.missingYearCount)
                HealthCount(R.string.settings_library_health_duration, snapshot.unknownDurationCount)
                HealthCount(R.string.settings_library_health_size, snapshot.unknownSizeCount)
                LibraryStatsNote(stringResource(R.string.settings_library_health_metadata_scope))
            }
        }
    }
}

@Composable
internal fun SettingsLibraryLyricsPanel(
    lyrics: LibraryLyricsInspection?,
    trackCount: Int,
    requested: Boolean,
    isScanning: Boolean,
    failed: Boolean,
    onToggleInspection: () -> Unit,
) {
    val inspection = lyrics ?: LibraryLyricsInspection().takeIf { trackCount == 0 }
    LibraryStatsSection(stringResource(R.string.settings_library_health_lyrics_title)) {
        LibraryStatsDivider()
        HealthCount(R.string.settings_library_health_lyrics_found, inspection?.foundCount)
        HealthCount(R.string.settings_library_health_lyrics_missing, inspection?.missingCount)
        HealthCount(R.string.settings_library_health_lyrics_unverified, inspection?.unverifiedCount)
        HealthCount(R.string.settings_library_health_lyrics_pending, inspection?.pendingCount ?: trackCount)
        if (requested && inspection != null) LibraryStatsNote(stringResource(
            R.string.settings_library_health_progress, inspection.checkedCount, inspection.totalCount,
        ))
        if (requested && inspection?.waitingForPlayback == true) LibraryStatsNote(stringResource(
            R.string.settings_library_health_playback_wait,
        ))
        if (failed) LibraryStatsNote(stringResource(R.string.settings_library_health_lyrics_error))
        TextButton(onClick = onToggleInspection, enabled = !isScanning && trackCount > 0) {
            Text(stringResource(if (requested) R.string.settings_library_health_stop
                else R.string.settings_library_health_check_lyrics))
        }
        LibraryStatsDisclosure(stringResource(R.string.settings_library_inspection_details)) {
            if (inspection?.checkedAtEpochMs != null && inspection.checkedAtEpochMs > 0L) LibraryStatsNote(stringResource(
                R.string.settings_library_health_checked_at,
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(java.util.Date(inspection.checkedAtEpochMs)),
            ))
            LibraryStatsNote(stringResource(R.string.settings_library_health_lyrics_scope))
        }
    }
}

@Composable
private fun HealthCount(label: Int, count: Int?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Text(if (count == null) stringResource(R.string.settings_library_health_not_checked)
            else stringResource(R.string.settings_library_health_count, count),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
