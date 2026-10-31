package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@Composable
internal fun SettingsLibraryContent(
    subNav: SettingsSubNav,
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
    // The entry list (and two panes) lead with the everyday rows and the library totals.
    if (subNav.page == null) {
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
    }
    if (subNav.isHub) {
        SettingsSubPageRow(SettingsSubPage.LibraryHealth, Icons.Rounded.Insights, subNav)
        SettingsSubPageRow(SettingsSubPage.LibraryMaintenance, Icons.Rounded.Build, subNav)
        return
    }
    // Statistics load only while their own page is open.
    if (subNav.shows(SettingsSubPage.LibraryHealth)) {
        SettingsLibraryHealthContent(
            isActive = isActive && subNav.shows(SettingsSubPage.LibraryHealth),
            isScanning = isScanning,
            trackCount = trackCount,
            durationMs = durationMs,
            sizeBytes = localSizeBytes,
            onLoadHealth = onLoadHealth,
            onLoadAudioProfile = onLoadAudioProfile,
            onInspectLyrics = onInspectLyrics,
        )
    }
    if (subNav.shows(SettingsSubPage.LibraryMaintenance)) {
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

}
