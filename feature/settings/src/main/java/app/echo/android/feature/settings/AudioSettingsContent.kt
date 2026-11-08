package app.echo.android.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.*
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import kotlin.math.roundToInt

/** Audio policy and transitions, wired to the same persisted settings as before. */
@Composable
fun AudioSettingsContent(
    compactMode: Boolean,
    audioFocusEnabled: Boolean,
    audioInterruptionPolicy: String,
    trackTransitions: EchoTrackTransitionOptions,
    usbExclusiveEnabled: Boolean,
    usbBitPerfectEnabled: Boolean,
    effectivePerformanceMode: String,
    replayGainEnabled: Boolean,
    replayGainMode: String,
    replayGainPreampDb: Float,
    audioDiagnostics: EchoPlaybackDiagnostics,
    systemEffectsLocalPlayback: Boolean,
    onAudioFocusEnabledChange: (Boolean) -> Unit,
    onAudioInterruptionPolicyChange: (String) -> Unit,
    onTrackTransitionsChange: (EchoTrackTransitionOptions) -> Unit,
    onReplayGainChange: (Boolean, Float) -> Unit,
    onReplayGainModeChange: (EchoReplayGainMode) -> Unit,
    onResetAudioOutput: () -> Unit,
    onOpenSystemAudioEffects: () -> Unit,
) {
    CompositionLocalProvider(LocalSettingsCompactMode provides compactMode) {
        MaterialTheme(shapes = SettingsShapes) {
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
    }
}
