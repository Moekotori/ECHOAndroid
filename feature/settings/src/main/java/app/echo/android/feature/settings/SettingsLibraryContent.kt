package app.echo.android.feature.settings

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.echo.android.model.library.LibraryOfflinePolicy

@Composable
internal fun SettingsLibraryContent(
    trackAudioInfoTagsVisible: Boolean,
    watchedFolderRescanEnabled: Boolean,
    offlineWifiOnly: Boolean,
    offlineUsedBytes: Long,
    onTrackAudioInfoTagsVisibleChange: (Boolean) -> Unit,
    onWatchedFolderRescanEnabledChange: (Boolean) -> Unit,
    onOfflineWifiOnlyChange: (Boolean) -> Unit,
    onOpenLibrary: () -> Unit,
    onClearLocalLibraryIndex: suspend () -> Boolean,
    onCleanupLocalLibrary: suspend () -> Pair<Int, Int> = { 0 to 0 },
) {
    val context = LocalContext.current
    SettingsSectionCard(
        title = stringResource(R.string.settings_section_library),
    ) {
        SettingsSwitchRow(
            title = stringResource(R.string.settings_audio_tags),
            detail = stringResource(R.string.settings_audio_tags_detail),
            checked = trackAudioInfoTagsVisible,
            onCheckedChange = onTrackAudioInfoTagsVisibleChange,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_watched_folder_rescan),
            detail = stringResource(R.string.settings_watched_folder_rescan_detail),
            checked = watchedFolderRescanEnabled,
            onCheckedChange = onWatchedFolderRescanEnabledChange,
        )
        SettingsSwitchRow(
            title = stringResource(R.string.settings_offline_wifi_only),
            detail = stringResource(R.string.settings_offline_wifi_only_detail),
            checked = offlineWifiOnly,
            onCheckedChange = onOfflineWifiOnlyChange,
        )
        SettingsInfoRow(
            title = stringResource(R.string.settings_offline_storage),
            detail = stringResource(
                R.string.settings_offline_storage_detail,
                Formatter.formatFileSize(context, offlineUsedBytes.coerceAtLeast(0L)),
                Formatter.formatFileSize(context, LibraryOfflinePolicy.DefaultQuotaBytes),
            ),
        )
        SettingsActionRow(
            title = stringResource(R.string.settings_local_music),
            detail = stringResource(R.string.settings_local_music_detail),
            onClick = onOpenLibrary,
        )
        SettingsLibraryCleanupRow(onCleanupLocalLibrary)
        SettingsClearLibraryIndexRow(onClearLocalLibraryIndex)
    }
}
