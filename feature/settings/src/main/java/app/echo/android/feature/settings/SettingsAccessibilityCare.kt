package app.echo.android.feature.settings

import androidx.compose.runtime.Composable
import app.echo.android.model.playback.EchoChannelBalanceState
import app.echo.android.model.playback.EchoPlaybackStatus

/** The app supplies existing settings and playback actions; this feature owns the controls. */
@Composable
fun SettingsAccessibilityCareContent(
    status: EchoPlaybackStatus,
    channelBalance: EchoChannelBalanceState,
    processingBypassed: Boolean,
    lyricsLineSpacing: Float,
    lyricsShowTranslation: Boolean,
    lyricsShowRomanization: Boolean,
    replayGainEnabled: Boolean,
    replayGainPreampDb: Float,
    pauseOnDisconnect: Boolean,
    resumeOnReconnect: Boolean,
    onLyricsLineSpacingChange: (Float) -> Unit,
    onLyricsShowTranslationChange: (Boolean) -> Unit,
    onLyricsShowRomanizationChange: (Boolean) -> Unit,
    onChannelBalanceChange: (EchoChannelBalanceState) -> Unit,
    onReplayGainChange: (Boolean, Float) -> Unit,
    onPauseOnDisconnectChange: (Boolean) -> Unit,
    onResumeOnReconnectChange: (Boolean) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onSetSleepTimerEndOfTrack: () -> Unit,
    onCancelSleepTimer: () -> Unit,
) {
    SettingsAccessibilityLyrics(
        lyricsLineSpacing, lyricsShowTranslation, lyricsShowRomanization,
        onLyricsLineSpacingChange, onLyricsShowTranslationChange, onLyricsShowRomanizationChange,
    )
    SettingsAccessibilityListening(
        channelBalance, processingBypassed, replayGainEnabled, replayGainPreampDb,
        pauseOnDisconnect, resumeOnReconnect, onChannelBalanceChange, onReplayGainChange,
        onPauseOnDisconnectChange, onResumeOnReconnectChange,
    )
    SettingsAccessibilityRest(status, onSetSleepTimer, onSetSleepTimerEndOfTrack, onCancelSleepTimer)
}
