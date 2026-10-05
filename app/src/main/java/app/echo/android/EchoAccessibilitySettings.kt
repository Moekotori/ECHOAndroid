package app.echo.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.data.EchoAppSettings
import app.echo.android.feature.settings.SettingsAccessibilityCareContent
import app.echo.android.model.playback.EchoPlaybackStatus

@Composable
internal fun EchoAccessibilityCareSettings(
    viewModel: EchoAndroidViewModel,
    settings: EchoAppSettings,
    status: EchoPlaybackStatus,
) {
    val channelBalance by viewModel.channelBalanceState.collectAsStateWithLifecycle()
    SettingsAccessibilityCareContent(
        status = status,
        channelBalance = channelBalance,
        processingBypassed = settings.usbBitPerfectEnabled || status.diagnostics.usbBitPerfectActive,
        lyricsLineSpacing = settings.lyricsLineSpacing,
        lyricsShowTranslation = settings.lyricsShowTranslation,
        lyricsShowRomanization = settings.lyricsShowRomanization,
        replayGainEnabled = settings.replayGainEnabled,
        replayGainPreampDb = settings.replayGainPreampDb,
        pauseOnDisconnect = settings.pauseOnAudioDisconnect,
        resumeOnReconnect = settings.resumeOnAudioReconnect,
        onLyricsLineSpacingChange = viewModel::setLyricsLineSpacing,
        onLyricsShowTranslationChange = viewModel::setLyricsShowTranslation,
        onLyricsShowRomanizationChange = viewModel::setLyricsShowRomanization,
        onChannelBalanceChange = viewModel::setChannelBalance,
        onReplayGainChange = viewModel::setReplayGain,
        onPauseOnDisconnectChange = viewModel::setPauseOnAudioDisconnect,
        onResumeOnReconnectChange = viewModel::setResumeOnAudioReconnect,
        onSetSleepTimer = viewModel::setSleepTimer,
        onSetSleepTimerEndOfTrack = viewModel::setSleepTimerEndOfTrack,
        onCancelSleepTimer = viewModel::cancelSleepTimer,
    )
}
