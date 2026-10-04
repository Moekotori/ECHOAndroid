package app.echo.android.playback

import app.echo.android.model.playback.EchoEqFilterType
import app.echo.android.model.playback.EchoMsebParameter
import app.echo.android.model.playback.EchoMsebState
import app.echo.android.model.playback.OpraEqBand
import kotlin.math.abs

/** ECHO's own hearing-oriented PEQ mapping; does not emulate a proprietary MSEB algorithm.
 * Texture and impulse controls shape the spectral impression, not a transient compressor.
 * At most eleven biquads; the neutral/disabled state contributes no processing.
 */
internal object EchoMsebEngine {
    fun filters(state: EchoMsebState): List<OpraEqBand> {
        if (!state.active) return emptyList()
        return buildList {
            fun band(parameter: EchoMsebParameter, hz: Float, maxDb: Float, q: Float,
                     type: String = EchoEqFilterType.PeakDip) {
                val gain = state.value(parameter) / 100f * maxDb
                if (abs(gain) > 0.0001f) {
                    add(OpraEqBand(type, hz, gain, q, null))
                }
            }
            band(EchoMsebParameter.Temperature, 350f, 3f, 0.707f, EchoEqFilterType.LowShelf)
            band(EchoMsebParameter.Temperature, 4000f, -3f, 0.707f, EchoEqFilterType.HighShelf)
            band(EchoMsebParameter.BassExtension, 70f, 6f, 0.707f, EchoEqFilterType.LowShelf)
            band(EchoMsebParameter.BassTexture, 110f, 4f, 0.9f)
            band(EchoMsebParameter.NoteThickness, 250f, 3f, 0.65f)
            band(EchoMsebParameter.Voice, 1200f, 4f, 0.65f)
            band(EchoMsebParameter.FemaleOvertones, 3000f, 3f, 1.5f)
            band(EchoMsebParameter.SibilanceLf, 5800f, 4.5f, 2f)
            band(EchoMsebParameter.SibilanceHf, 9200f, 4.5f, 2f)
            band(EchoMsebParameter.ImpulseResponse, 7500f, 3f, 0.45f)
            band(EchoMsebParameter.Air, 10000f, 4f, 0.707f, EchoEqFilterType.HighShelf)
        }
    }
}
