package app.echo.android.playback

import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.EchoChannelBalanceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@UnstableApi
class EchoChannelBalanceController {
    val processor = EchoChannelBalanceAudioProcessor { rate ->
        _state.update { it.copy(processingSampleRateHz = rate) }
    }

    private val _state = MutableStateFlow(EchoChannelBalanceState())
    val state: StateFlow<EchoChannelBalanceState> = _state.asStateFlow()

    private var lastShouldProcess: Boolean = false

    fun setState(state: EchoChannelBalanceState) {
        publish(state.normalized)
    }

    fun reset() {
        publish(EchoChannelBalanceState())
    }

    fun release() {
        processor.setRuntime(EchoChannelBalanceState())
        publish(EchoChannelBalanceState())
    }

    private fun publish(state: EchoChannelBalanceState) {
        processor.setRuntime(state)
        val shouldProcess = state.enabled
        if (shouldProcess != lastShouldProcess) {
            lastShouldProcess = shouldProcess
            if (!EchoPlaybackProcessRuntime.usbBitPerfectEnabled) {
                EchoPlaybackProcessRuntime.reconfigureAudioPipeline(forceSinkReset = true)
            }
        }
        _state.update { state.copy(processingSampleRateHz = it.processingSampleRateHz) }
    }
}
