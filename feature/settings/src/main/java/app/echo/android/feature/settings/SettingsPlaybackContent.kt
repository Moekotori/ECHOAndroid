package app.echo.android.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.design.LocalEchoPlatformCapabilities

@Composable
internal fun SettingsPlaybackContent(
    subNav: SettingsSubNav,
    onlineLyricsEnabled: Boolean,
    lockScreenLyricsEnabled: Boolean,
    floatingLyrics: app.echo.android.model.settings.EchoFloatingLyricsSettings,
    floatingLyricsPermissionGranted: Boolean,
    lyricsOptions: app.echo.android.model.settings.EchoLyricsOptions,
    onLyricsOptionsChange: (app.echo.android.model.settings.EchoLyricsOptions) -> Unit,
    onOpenLyricsInterface: () -> Unit,
    playerOrientationLocked: Boolean = false,
    onPlayerOrientationLockedChange: (Boolean) -> Unit = {},
    pauseOnMute: Boolean = false,
    onPauseOnMuteChange: (Boolean) -> Unit = {},
    shuffleOptions: app.echo.android.model.playback.EchoShuffleOptions,
    onShuffleOptionsChange: (app.echo.android.model.playback.EchoShuffleOptions) -> Unit,
    sleepTimerFinishTrack: Boolean = true,
    stopOnTaskRemoved: Boolean = false,
    onStopOnTaskRemovedChange: (Boolean) -> Unit = {},
    onSleepTimerFinishTrackChange: (Boolean) -> Unit = {},
    onOnlineLyricsEnabledChange: (Boolean) -> Unit,
    onLockScreenLyricsEnabledChange: (Boolean) -> Unit,
    onFloatingLyricsChange: (app.echo.android.model.settings.EchoFloatingLyricsSettings) -> Unit,
    onRequestFloatingLyricsPermission: () -> Unit,
    notificationPermissionGranted: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
) {
    if (subNav.isHub) {
        SettingsSubPageRow(SettingsSubPage.PlaybackListening, Icons.Rounded.Headphones, subNav)
        SettingsSubPageRow(SettingsSubPage.PlaybackLyrics, Icons.Rounded.Lyrics, subNav)
        return
    }
    if (subNav.shows(SettingsSubPage.PlaybackListening)) {
        SettingsShuffleModeRow(shuffleOptions, onShuffleOptionsChange)
        SettingsPlayNextBehaviorRow(shuffleOptions, onShuffleOptionsChange)
        SettingsSectionCard(title = stringResource(R.string.settings_section_listening)) {
            SettingsSwitchRow(title = stringResource(R.string.pause_on_mute), detail = stringResource(R.string.pause_on_mute_hint),
                checked = pauseOnMute, onCheckedChange = onPauseOnMuteChange)
            SettingsSwitchRow(title = stringResource(R.string.stop_on_task_removed_title),
                detail = stringResource(R.string.stop_on_task_removed_detail),
                checked = stopOnTaskRemoved, onCheckedChange = onStopOnTaskRemovedChange)
            SettingsSwitchRow(title = stringResource(R.string.listening_finish_track),
                detail = stringResource(R.string.listening_finish_track_detail),
                checked = sleepTimerFinishTrack, onCheckedChange = onSleepTimerFinishTrackChange)
        }
    }
    if (subNav.shows(SettingsSubPage.PlaybackLyrics)) {
        SettingsSectionCard(title = stringResource(R.string.settings_section_lyrics)) {
            SettingsActionRow(
                title = stringResource(R.string.settings_lyrics_interface),
                detail = stringResource(R.string.settings_lyrics_interface_detail),
                onClick = onOpenLyricsInterface,
            )
            SettingsLyricsSourceRow(lyricsOptions, onLyricsOptionsChange)
            SettingsBluetoothLyricsDelay(lyricsOptions, onLyricsOptionsChange)
            SettingsSwitchRow(title = stringResource(R.string.player_orientation_lock), detail = stringResource(R.string.player_orientation_lock_hint),
                checked = playerOrientationLocked, onCheckedChange = onPlayerOrientationLockedChange)
            SettingsSwitchRow(
                title = stringResource(R.string.settings_online_lyrics),
                detail = stringResource(R.string.settings_online_lyrics_detail),
                checked = onlineLyricsEnabled,
                onCheckedChange = onOnlineLyricsEnabledChange,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_lock_lyrics),
                detail = stringResource(R.string.settings_lock_lyrics_detail),
                checked = lockScreenLyricsEnabled,
                onCheckedChange = onLockScreenLyricsEnabledChange,
            )
            SettingsFloatingLyricsRows(
                settings = floatingLyrics,
                permissionGranted = floatingLyricsPermissionGranted,
                onChange = onFloatingLyricsChange,
                onRequestPermission = onRequestFloatingLyricsPermission,
            )
            SettingsStatusLyricsRows(
                options = lyricsOptions,
                permissionGranted = floatingLyricsPermissionGranted,
                onChange = onLyricsOptionsChange,
                onRequestPermission = onRequestFloatingLyricsPermission,
            )
            val notificationRuntimePermission =
                LocalEchoPlatformCapabilities.current.notificationRuntimePermission
            if (notificationRuntimePermission && !notificationPermissionGranted) {
                SettingsActionRow(
                    title = stringResource(R.string.settings_notification_permission),
                    detail = stringResource(R.string.settings_notification_denied),
                    actionLabel = stringResource(R.string.settings_allow),
                    onClick = onRequestNotificationPermission,
                )
            }
        }
    }

}
