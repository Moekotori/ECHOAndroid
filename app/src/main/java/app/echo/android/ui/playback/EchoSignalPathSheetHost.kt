package app.echo.android.ui.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.EchoAndroidViewModel
import app.echo.android.feature.settings.SignalPathSheet
import app.echo.android.model.playback.EchoDspSettings
import app.echo.android.model.playback.EchoPlaybackStatus

@Composable
internal fun EchoSignalPathSheetHost(
    viewModel: EchoAndroidViewModel,
    status: EchoPlaybackStatus,
    dsp: EchoDspSettings,
    onDismiss: () -> Unit,
) {
    val equalizer by viewModel.equalizerState.collectAsStateWithLifecycle()
    val balance by viewModel.channelBalanceState.collectAsStateWithLifecycle()
    val volume by viewModel.volumeSettings.collectAsStateWithLifecycle()
    SignalPathSheet(status, equalizer, balance, dsp, volume, onDismiss)
}
