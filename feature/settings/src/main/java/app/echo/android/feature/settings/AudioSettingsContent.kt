package app.echo.android.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import app.echo.android.model.playback.*

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
    status: EchoPlaybackStatus,
    pauseOnDisconnect: Boolean,
    resumeOnReconnect: Boolean,
    systemEffectsLocalPlayback: Boolean,
    onAudioFocusEnabledChange: (Boolean) -> Unit,
    onAudioInterruptionPolicyChange: (String) -> Unit,
    onTrackTransitionsChange: (EchoTrackTransitionOptions) -> Unit,
    onReplayGainChange: (Boolean, Float) -> Unit,
    onReplayGainModeChange: (EchoReplayGainMode) -> Unit,
    onResetAudioOutput: () -> Unit,
    onOpenSystemAudioEffects: () -> Unit,
    onOpenUsbOutput: () -> Unit,
    onPauseOnDisconnect: (Boolean) -> Unit,
    onResumeOnReconnect: (Boolean) -> Unit,
) {
    CompositionLocalProvider(LocalSettingsCompactMode provides compactMode) {
        MaterialTheme(shapes = SettingsShapes) {
            AudioOutputSettingsSection(status, onOpenUsbOutput)
            SettingsAudioOutputContent(
                audioFocusEnabled = audioFocusEnabled,
                audioInterruptionPolicy = audioInterruptionPolicy,
                pauseOnDisconnect = pauseOnDisconnect,
                resumeOnReconnect = resumeOnReconnect,
                onAudioFocusEnabledChange = onAudioFocusEnabledChange,
                onAudioInterruptionPolicyChange = onAudioInterruptionPolicyChange,
                onPauseOnDisconnect = onPauseOnDisconnect,
                onResumeOnReconnect = onResumeOnReconnect,
            )
            AudioReplayGainSettings(replayGainEnabled, replayGainMode, replayGainPreampDb,
                usbBitPerfectEnabled, onReplayGainChange, onReplayGainModeChange)
            AudioTransitionSettings(trackTransitions, usbExclusiveEnabled, usbBitPerfectEnabled,
                effectivePerformanceMode, onTrackTransitionsChange)
            SystemAudioEffectsControl(status.diagnostics, systemEffectsLocalPlayback, onOpenSystemAudioEffects)
            AudioSettingsReset(onResetAudioOutput)
        }
    }
}
