package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@Composable
internal fun SettingsLibraryContent(
    isActive: Boolean,
    isScanning: Boolean,
    onLoadHealth: suspend () -> app.echo.android.model.library.LibraryHealthStats,
    onLoadAudioProfile: suspend () -> app.echo.android.model.library.LibraryAudioProfile,
    onInspectLyrics: suspend ((app.echo.android.model.library.LibraryLyricsInspection) -> Unit) -> Unit,
    trackCount: Int,
    albumCount: Int,
    artistCount: Int,
    durationMs: Long,
    localSizeBytes: Long,
    trackAudioInfoTagsVisible: Boolean,
    watchedFolderRescanEnabled: Boolean,
    onTrackAudioInfoTagsVisibleChange: (Boolean) -> Unit,
    onWatchedFolderRescanEnabledChange: (Boolean) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenLibraryRepair: () -> Unit,
    onClearLocalLibraryIndex: suspend () -> Boolean,
    onCleanupLocalLibrary: suspend () -> Pair<Int, Int> = { 0 to 0 },
) {
    Column(Modifier.fillMaxWidth()
        .settingsSearchAnchor(stringResource(R.string.settings_section_library))
        .padding(horizontal = SettingsContentInset)) {
        SettingsActionRow(
            title = stringResource(R.string.settings_local_music),
            detail = stringResource(R.string.settings_local_music_detail),
            onClick = onOpenLibrary,
        )
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
    }
    SettingsLibraryOverview(
        trackCount = trackCount,
        albumCount = albumCount,
        artistCount = artistCount,
        durationMs = durationMs,
        localSizeBytes = localSizeBytes,
        onOpenLibrary = onOpenLibrary,
    )
    SettingsLibraryHealthContent(
        isActive = isActive,
        isScanning = isScanning,
        trackCount = trackCount,
        durationMs = durationMs,
        sizeBytes = localSizeBytes,
        onLoadHealth = onLoadHealth,
        onLoadAudioProfile = onLoadAudioProfile,
        onInspectLyrics = onInspectLyrics,
    )
    SettingsSectionCard(title = stringResource(R.string.settings_section_maintenance), secondary = true) {
        SettingsActionRow(
            title = stringResource(R.string.settings_library_repair),
            detail = stringResource(R.string.settings_library_repair_detail),
            onClick = onOpenLibraryRepair,
        )
        SettingsLibraryCleanupRow(onCleanupLocalLibrary)
        SettingsClearLibraryIndexRow(onClearLocalLibraryIndex)
    }
}
