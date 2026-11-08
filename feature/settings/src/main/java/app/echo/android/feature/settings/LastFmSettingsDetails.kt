package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.settings.LastFmActivity
import app.echo.android.model.settings.LastFmImportPhase
import app.echo.android.model.settings.LastFmImportStatus
import app.echo.android.model.settings.LastFmLoveSyncStatus
import app.echo.android.model.settings.LastFmOptions
import java.text.DateFormat
import java.util.Date

@Composable
fun LastFmSettingsDetails(
    enabled: Boolean,
    connected: Boolean,
    options: LastFmOptions,
    activity: LastFmActivity,
    importStatus: LastFmImportStatus,
    loveStatus: LastFmLoveSyncStatus,
    onOptionsChange: (LastFmOptions) -> Unit,
    onImport: () -> Unit,
    onCancelImport: () -> Unit,
    onRefresh: () -> Unit,
) {
    SettingsSwitchRow(stringResource(R.string.lastfm_now_playing_title), stringResource(R.string.lastfm_now_playing_detail),
        options.nowPlayingEnabled, { onOptionsChange(options.copy(nowPlayingEnabled = it)) })
    SettingsSwitchRow(stringResource(R.string.lastfm_scrobble_title), stringResource(R.string.lastfm_scrobble_detail),
        options.scrobbleEnabled, { onOptionsChange(options.copy(scrobbleEnabled = it)) })
    SettingsSwitchRow(stringResource(R.string.lastfm_love_title), stringResource(R.string.lastfm_love_detail),
        options.loveSyncEnabled, { onOptionsChange(options.copy(loveSyncEnabled = it)) },
        enabled = options.loveSyncEnabled || (enabled && connected))
    if (options.loveSyncEnabled) {
        SettingsInfoRow(stringResource(R.string.lastfm_love_title), when {
            !enabled || !connected -> stringResource(R.string.lastfm_connect_first)
            loveStatus.capacityReached -> stringResource(R.string.lastfm_capacity)
            loveStatus.failed -> stringResource(R.string.lastfm_love_failed)
            loveStatus.scanning -> stringResource(R.string.lastfm_love_scanning)
            else -> stringResource(R.string.lastfm_love_progress, loveStatus.synced, loveStatus.pending)
        })
    }
    val running = importStatus.phase == LastFmImportPhase.Running
    SettingsActionRow(title = stringResource(R.string.lastfm_import_title),
        detail = stringResource(R.string.lastfm_import_detail), enabled = enabled && connected && !running, onClick = onImport)
    if (running) SettingsActionRow(title = stringResource(R.string.lastfm_import_cancel),
        detail = stringResource(R.string.lastfm_import_running, importStatus.processed), onClick = onCancelImport)
    if (importStatus.phase != LastFmImportPhase.Idle) {
        val phase = when (importStatus.phase) {
            LastFmImportPhase.Completed -> R.string.lastfm_import_completed
            LastFmImportPhase.Cancelled -> R.string.lastfm_import_cancelled
            LastFmImportPhase.Failed -> R.string.lastfm_import_failed
            else -> R.string.lastfm_import_title
        }
        SettingsInfoRow(stringResource(phase), stringResource(R.string.lastfm_import_result,
            importStatus.lovedAdded, importStatus.playCountsUpdated, importStatus.unmatched, importStatus.ambiguous))
    }
    if (enabled) {
        SettingsInfoRow(stringResource(R.string.lastfm_last_now_playing), lastFmTime(activity.lastNowPlayingAt))
        SettingsInfoRow(stringResource(R.string.lastfm_last_scrobble), lastFmTime(activity.lastScrobbleAt))
        Column(Modifier.fillMaxWidth()) {
            SettingsInfoRow(stringResource(R.string.lastfm_active_track), if (activity.title.isBlank())
                stringResource(R.string.lastfm_no_active_track) else stringResource(R.string.lastfm_active_progress,
                activity.artist, activity.title, activity.playedSeconds, activity.thresholdSeconds))
            if (activity.title.isNotBlank() && activity.thresholdSeconds > 0 && options.scrobbleEnabled) {
                LinearProgressIndicator(
                    progress = { (activity.playedSeconds.toFloat() / activity.thresholdSeconds).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        }
        if (activity.pendingCount > 0) SettingsInfoRow(stringResource(R.string.lastfm_pending_title),
            stringResource(R.string.lastfm_pending_detail, activity.pendingCount))
    }
    SettingsActionRow(title = stringResource(R.string.lastfm_refresh), detail = stringResource(R.string.lastfm_refresh_detail), onClick = onRefresh)
}

@Composable
private fun lastFmTime(epochMs: Long?): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    return if (epochMs == null) stringResource(R.string.lastfm_never) else remember(epochMs, locale) {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, locale).format(Date(epochMs))
    }
}
