package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.echo.android.model.library.LibraryHealthStats
import app.echo.android.model.library.LibraryLyricsInspection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.text.DateFormat

@Composable
internal fun SettingsLibraryHealthContent(
    isActive: Boolean,
    isScanning: Boolean,
    trackCount: Int,
    durationMs: Long,
    sizeBytes: Long,
    onLoadHealth: suspend () -> LibraryHealthStats,
    onInspectLyrics: suspend ((LibraryLyricsInspection) -> Unit) -> Unit,
) {
    var health by remember { mutableStateOf<LibraryHealthStats?>(null) }
    var lyrics by remember { mutableStateOf<LibraryLyricsInspection?>(null) }
    var requested by remember { mutableStateOf(false) }
    var healthFailed by remember { mutableStateOf(false) }
    var lyricsFailed by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val load by rememberUpdatedState(onLoadHealth)
    val inspect by rememberUpdatedState(onInspectLyrics)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val visible = isActive && foreground

    // Only the visible category subscribes to work. Let an active scan finish first.
    LaunchedEffect(visible, isScanning, trackCount, durationMs, sizeBytes, refresh) {
        if (!visible || isScanning) return@LaunchedEffect
        delay(300L)
        healthFailed = false
        try { health = load() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { healthFailed = true }
    }
    LaunchedEffect(visible, isScanning, requested, trackCount, durationMs, sizeBytes) {
        if (!visible || isScanning || !requested) {
            requested = false
            return@LaunchedEffect
        }
        lyricsFailed = false
        lyrics = LibraryLyricsInspection(totalCount = trackCount)
        try { inspect { lyrics = it } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { lyricsFailed = true }
        finally { requested = false }
    }

    SettingsSectionCard(stringResource(R.string.settings_library_health_title)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { refresh++ }, enabled = !isScanning && !requested) {
                Text(stringResource(R.string.settings_library_health_refresh))
            }
        }
        if (isScanning) HealthNote(stringResource(R.string.settings_library_health_scanning))
        else if (healthFailed) HealthNote(stringResource(R.string.settings_library_health_error))
        else if (health == null) HealthNote(stringResource(R.string.settings_library_health_loading))
        health?.let { snapshot ->
            HealthCount(R.string.settings_library_health_folders, snapshot.folderCount)
            HealthCount(R.string.settings_library_health_cover, snapshot.missingCoverCount)
            HealthCount(R.string.settings_library_health_album, snapshot.missingAlbumCount)
            HealthCount(R.string.settings_library_health_artist, snapshot.missingArtistCount)
            HealthCount(R.string.settings_library_health_genre, snapshot.missingGenreCount)
            HealthCount(R.string.settings_library_health_year, snapshot.missingYearCount)
            HealthCount(R.string.settings_library_health_duration, snapshot.unknownDurationCount)
            HealthCount(R.string.settings_library_health_size, snapshot.unknownSizeCount)
        }
        HealthNote(stringResource(R.string.settings_library_health_metadata_scope))
    }
    SettingsSectionCard(stringResource(R.string.settings_library_health_lyrics_title)) {
        val inspection = lyrics ?: LibraryLyricsInspection().takeIf { trackCount == 0 }
        HealthCount(R.string.settings_library_health_lyrics_found, inspection?.foundCount)
        HealthCount(R.string.settings_library_health_lyrics_missing, inspection?.missingCount)
        HealthCount(R.string.settings_library_health_lyrics_unverified, inspection?.unverifiedCount)
        HealthCount(R.string.settings_library_health_lyrics_pending, inspection?.pendingCount ?: trackCount)
        if (requested && inspection != null) HealthNote(stringResource(
            R.string.settings_library_health_progress, inspection.checkedCount, inspection.totalCount,
        ))
        if (requested && inspection?.waitingForPlayback == true) HealthNote(stringResource(
            R.string.settings_library_health_playback_wait,
        ))
        if (inspection?.checkedAtEpochMs != null && inspection.checkedAtEpochMs > 0L) HealthNote(stringResource(
            R.string.settings_library_health_checked_at,
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(java.util.Date(inspection.checkedAtEpochMs)),
        ))
        if (lyricsFailed) HealthNote(stringResource(R.string.settings_library_health_lyrics_error))
        HealthNote(stringResource(R.string.settings_library_health_lyrics_scope))
        TextButton(onClick = { requested = !requested }, enabled = !isScanning && trackCount > 0) {
            Text(stringResource(if (requested) R.string.settings_library_health_stop else R.string.settings_library_health_check_lyrics))
        }
    }
}

@Composable
private fun HealthCount(label: Int, count: Int?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (count == null) stringResource(R.string.settings_library_health_not_checked)
            else stringResource(R.string.settings_library_health_count, count),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun HealthNote(text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
