package app.echo.android.playback

import app.echo.android.model.playback.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Low-frequency lifecycle events only; independent of UI and sample buffers. */
class EchoAudioPipelineState {
    internal var systemEffectsSession: (() -> Int?)? = null
    private val mutable = MutableStateFlow(EchoAudioPipelineDiagnostics())
    val diagnostics = mutable.asStateFlow()
    internal fun decoder(value: EchoDecoderDiagnostics) { mutable.update { it.copy(decoder = value) } }
    private var converterOwner: Any? = null
    @Synchronized internal fun conversion(owner: Any, value: EchoUsbConversionDiagnostics?) {
        if (value == null && converterOwner !== owner) return
        converterOwner = if (value == null) null else owner
        mutable.update { it.copy(usbConversion = value) }
    }
}

/** Application-owned dependency used by Android-created services and the app controller. */
interface EchoAudioPipelineOwner {
    val audioPipelineState: EchoAudioPipelineState
}
