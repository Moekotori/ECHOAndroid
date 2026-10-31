package app.echo.android.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.design.LocalEchoPlatformCapabilities
import app.echo.android.model.playback.EchoReplayGainMode
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import kotlin.math.roundToInt

@Composable
internal fun SettingsPlaybackContent(
    subNav: SettingsSubNav,
    playbackHapticsEnabled: Boolean,
    effectivePerformanceMode: String,
    onlineLyricsEnabled: Boolean,
    lockScreenLyricsEnabled: Boolean,
    floatingLyrics: app.echo.android.model.settings.EchoFloatingLyricsSettings,
    floatingLyricsPermissionGranted: Boolean,
    lyricsOptions: app.echo.android.model.settings.EchoLyricsOptions,
    onLyricsOptionsChange: (app.echo.android.model.settings.EchoLyricsOptions) -> Unit,
    onOpenLyricsInterface: () -> Unit,
    usbExclusiveEnabled: Boolean,
    usbBitPerfectEnabled: Boolean,
    shuffleOptions: app.echo.android.model.playback.EchoShuffleOptions,
    onShuffleOptionsChange: (app.echo.android.model.playback.EchoShuffleOptions) -> Unit,
    trackTransitions: app.echo.android.model.playback.EchoTrackTransitionOptions,
    audioFocusEnabled: Boolean = true,
    onAudioFocusEnabledChange: (Boolean) -> Unit = {},
    onResetAudioOutput: () -> Unit = {},
    audioInterruptionPolicy: String = "duck",
    sleepTimerFinishTrack: Boolean = true,
    audioDiagnostics: app.echo.android.model.playback.EchoPlaybackDiagnostics = app.echo.android.model.playback.EchoPlaybackDiagnostics(),
    onOpenSystemAudioEffects: () -> Unit = {},
    systemEffectsLocalPlayback: Boolean = true,
    stopOnTaskRemoved: Boolean = false,
    onStopOnTaskRemovedChange: (Boolean) -> Unit = {},
    onAudioInterruptionPolicyChange: (String) -> Unit = {},
    onSleepTimerFinishTrackChange: (Boolean) -> Unit = {},
    pauseOnAudioDisconnect: Boolean,
    resumeOnAudioReconnect: Boolean,
    replayGainEnabled: Boolean,
    replayGainMode: String,
    replayGainPreampDb: Float,
    onPlaybackHapticsEnabledChange: (Boolean) -> Unit,
    onOnlineLyricsEnabledChange: (Boolean) -> Unit,
    onLockScreenLyricsEnabledChange: (Boolean) -> Unit,
    onFloatingLyricsChange: (app.echo.android.model.settings.EchoFloatingLyricsSettings) -> Unit,
    onRequestFloatingLyricsPermission: () -> Unit,
    onTrackTransitionsChange: (app.echo.android.model.playback.EchoTrackTransitionOptions) -> Unit,
    onPauseOnAudioDisconnectChange: (Boolean) -> Unit,
    onResumeOnAudioReconnectChange: (Boolean) -> Unit,
    onReplayGainChange: (Boolean, Float) -> Unit,
    onReplayGainModeChange: (EchoReplayGainMode) -> Unit,
    notificationPermissionGranted: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
) {
    if (subNav.isHub) {
        SettingsSubPageRow(SettingsSubPage.PlaybackOutput, Icons.Rounded.GraphicEq, subNav)
        SettingsSubPageRow(SettingsSubPage.PlaybackListening, Icons.Rounded.Headphones, subNav)
        SettingsSubPageRow(SettingsSubPage.PlaybackLyrics, Icons.Rounded.Lyrics, subNav)
        return
    }
    if (subNav.shows(SettingsSubPage.PlaybackOutput)) {
        SettingsAudioOutputContent(
            audioFocusEnabled = audioFocusEnabled,
            audioInterruptionPolicy = audioInterruptionPolicy,
            trackTransitions = trackTransitions,
            usbBitPerfectEnabled = usbBitPerfectEnabled,
            replayGainEnabled = replayGainEnabled,
            replayGainMode = replayGainMode,
            replayGainPreampDb = replayGainPreampDb,
            onAudioFocusEnabledChange = onAudioFocusEnabledChange,
            onAudioInterruptionPolicyChange = onAudioInterruptionPolicyChange,
            onTrackTransitionsChange = onTrackTransitionsChange,
            onReplayGainChange = onReplayGainChange,
            onReplayGainModeChange = onReplayGainModeChange,
            onResetAudioOutput = onResetAudioOutput,
        )
        SystemAudioEffectsControl(audioDiagnostics, systemEffectsLocalPlayback, onOpenSystemAudioEffects)
        SettingsSectionCard(title = stringResource(R.string.settings_section_transition), secondary = true) {
            SettingsInfoRow(title = stringResource(R.string.settings_gapless),
                detail = stringResource(R.string.settings_gapless_detail))
            SettingsSwitchRow(
                title = stringResource(R.string.settings_track_fade),
                detail = stringResource(if (usbBitPerfectEnabled) R.string.settings_track_fade_bypass else R.string.settings_track_fade_detail),
                checked = trackTransitions.fadeEnabled,
                onCheckedChange = { onTrackTransitionsChange(trackTransitions.copy(fadeEnabled = it)) },
            )
            if (trackTransitions.fadeEnabled) SettingsSliderRow(
                title = stringResource(R.string.settings_track_fade_duration),
                valueLabel = { stringResource(R.string.settings_track_fade_seconds, it) },
                value = trackTransitions.fadeDurationMs / 1000f,
                valueRange = 0.5f..5f,
                steps = 8,
                onValueChange = {
                    onTrackTransitionsChange(
                        trackTransitions.copy(fadeDurationMs = (it * 1000f).roundToInt()),
                    )
                },
            )
            val smartBypassed = usbExclusiveEnabled || usbBitPerfectEnabled ||
                effectivePerformanceMode == EchoEffectivePerformanceMode.Lightweight.id
            SettingsAutoMixCard(
                checked = trackTransitions.smartEnabled,
                bypassed = smartBypassed,
                onCheckedChange = { onTrackTransitionsChange(trackTransitions.copy(smartEnabled = it)) },
            )
        }
    }
    if (subNav.shows(SettingsSubPage.PlaybackListening)) {
        SettingsShuffleModeRow(shuffleOptions, onShuffleOptionsChange)
        SettingsSectionCard(title = stringResource(R.string.settings_section_listening)) {
            SettingsSwitchRow(title = stringResource(R.string.stop_on_task_removed_title),
                detail = stringResource(R.string.stop_on_task_removed_detail),
                checked = stopOnTaskRemoved, onCheckedChange = onStopOnTaskRemovedChange)
            SettingsSwitchRow(title = stringResource(R.string.listening_finish_track),
                detail = stringResource(R.string.listening_finish_track_detail),
                checked = sleepTimerFinishTrack, onCheckedChange = onSleepTimerFinishTrackChange)
            SettingsSwitchRow(
                title = stringResource(R.string.settings_pause_on_disconnect),
                detail = stringResource(R.string.settings_pause_on_disconnect_detail),
                checked = pauseOnAudioDisconnect,
                onCheckedChange = onPauseOnAudioDisconnectChange,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_resume_on_reconnect),
                detail = stringResource(R.string.settings_resume_on_reconnect_detail),
                checked = resumeOnAudioReconnect,
                onCheckedChange = onResumeOnAudioReconnectChange,
                enabled = pauseOnAudioDisconnect,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_playback_haptics),
                detail = stringResource(R.string.settings_playback_haptics_detail),
                checked = playbackHapticsEnabled,
                onCheckedChange = onPlaybackHapticsEnabledChange,
            )
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
