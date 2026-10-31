package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Numerical acceptance of the production PCM chain; headphone listening is a separate check. */
@UnstableApi
class HeadphoneCorrectionSignalTest {
    private val savedDsp = EchoPlaybackProcessRuntime.dspSettings
    private val savedReplayGain = EchoPlaybackProcessRuntime.dspReplayGainDb

    @After fun restoreDsp() {
        EchoPlaybackProcessRuntime.setDspSettings(savedDsp)
        EchoPlaybackProcessRuntime.dspReplayGainDb = savedReplayGain
    }

    @Test fun correctionPcmMatchesIndependentReferencesWithoutClippingOrChannelLeakage() {
        for ((preset, references) in profiles) {
            for ((rate, expected) in references) {
                frequencies.forEachIndexed { index, frequency ->
                    val measured = render(preset, rate, frequency, C.ENCODING_PCM_FLOAT, 4, false, 0.98, 0.0)
                    assertEquals("${preset.eqId}: $rate Hz / $frequency Hz", expected[index], measured, 0.08)
                }
            }
        }
    }

    @Test fun integerOutputPreservesCorrectionAndStereoAcrossBitDepthsAndByteOrders() {
        val formats = listOf(
            Triple(C.ENCODING_PCM_16BIT, 2, false), Triple(C.ENCODING_PCM_16BIT_BIG_ENDIAN, 2, true),
            Triple(C.ENCODING_PCM_24BIT, 3, false), Triple(C.ENCODING_PCM_24BIT_BIG_ENDIAN, 3, true),
            Triple(C.ENCODING_PCM_32BIT, 4, false), Triple(C.ENCODING_PCM_32BIT_BIG_ENDIAN, 4, true),
        )
        for ((preset, references) in profiles) for ((encoding, bytes, bigEndian) in formats) {
            val measured = render(preset, 48000, 1000, encoding, bytes, bigEndian, 0.1, -1.0)
            assertEquals("${preset.eqId}: encoding $encoding", references.getValue(48000)[2], measured, 0.08)
        }
    }

    @Test fun switchingAndRestoringPresetsPreservesEveryBandAndTheOriginalPreamp() {
        val controller = EchoEqualizerController()
        for ((preset, _) in profiles + profiles.reversed()) {
            controller.applyOpraPreset(preset)
            val state = controller.state.value
            assertTrue(state.enabled && state.parametric)
            assertEquals(preset.bands, state.filters)
            assertEquals(preset.preampDb, state.preampDb, 0f)
            assertEquals(preset.displayName, state.sourceLabel)
            assertEquals(preset.bands, controller.processingRuntime().filters)
            assertEquals(preset.preampDb, controller.processingRuntime().preampDb, 0f)
            val favorite = requireNotNull(EchoEqualizerUserPresets.captureOpra("favorite", "Correction", preset, 0))
            val restored = EchoEqualizerController()
            assertTrue(restored.applyUserPreset(favorite))
            assertEquals(controller.processingRuntime(), restored.processingRuntime())
            restored.setConfig(true, state.presetId, state.gainsDb, state.preampDb, state.filters, state.sourceLabel)
            assertEquals(controller.processingRuntime(), restored.processingRuntime())
        }
    }

    private fun render(
        preset: OpraHeadphoneCorrectionPreset, rate: Int, frequency: Int,
        encoding: Int, bytes: Int, bigEndian: Boolean, amplitude: Double, rightRatio: Double,
    ): Double {
        EchoPlaybackProcessRuntime.setDspSettings(EchoDspSettings())
        EchoPlaybackProcessRuntime.dspReplayGainDb = 0f
        val controller = EchoEqualizerController()
        controller.applyOpraPreset(preset)
        val processor = EchoDspAudioProcessor(arrayOf(controller.processor))
        assertEquals(encoding, processor.configure(AudioProcessor.AudioFormat(rate, 2, encoding)).encoding)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val frames = rate * 2 / 5
        val input = ByteBuffer.allocateDirect(frames * bytes * 2)
            .order(if (bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
        val floating = encoding == C.ENCODING_PCM_FLOAT
        val scale = (1L shl (bytes * 8 - 1)).toDouble()
        fun write(value: Double) {
            if (floating) input.putFloat(value.toFloat()) else {
                val integer = (value * scale).toLong()
                repeat(bytes) { index ->
                    input.put((integer shr ((if (bigEndian) bytes - 1 - index else index) * 8)).toByte())
                }
            }
        }
        repeat(frames) { frame ->
            val value = amplitude * sin(2 * PI * frequency * frame / rate)
            write(value)
            write(value * rightRatio)
        }
        input.flip()
        var frame = 0
        var count = 0
        var power = 0.0
        try {
            while (input.hasRemaining()) {
                processor.queueInput(input)
                val output = processor.output.order(input.order())
                fun read(): Double {
                    if (floating) return output.float.toDouble()
                    var value = 0L
                    repeat(bytes) { index ->
                        val shift = (if (bigEndian) bytes - 1 - index else index) * 8
                        value = value or ((output.get().toLong() and 255L) shl shift)
                    }
                    if (value and (1L shl (bytes * 8 - 1)) != 0L) value -= 1L shl (bytes * 8)
                    return value / scale
                }
                while (output.hasRemaining()) {
                    val left = read()
                    val right = read()
                    assertTrue("${preset.eqId}: non-finite/clipped PCM", left.isFinite() && abs(left) < 0.99999)
                    // Opposite-polarity channels quantize symmetrically; silence must stay exact.
                    val stereoTolerance = if (rightRatio == 0.0) 0.0 else 2.0 / scale
                    assertEquals("${preset.eqId}: stereo changed", left * rightRatio, right, stereoTolerance)
                    if (frame++ >= rate / 5) { power += left * left; count++ }
                }
            }
            assertEquals(frames, frame)
            assertEquals(rate, controller.state.value.processingSampleRateHz)
            return 20 * log10(sqrt(power / count) / (amplitude / sqrt(2.0)))
        } finally {
            processor.reset()
        }
    }
}

private val frequencies = listOf(50, 250, 1000, 5275, 10000)

private fun band(type: String, hz: Float, gain: Float, q: Float) = OpraEqBand(type, hz, gain, q, null)

// AutoEQ: bundled revision 7ae0f56d53074872b028649617a22bbb4232feb7,
// Auriculares Argentina / over-ear / Sennheiser HD 650. OPRA: parser's HD650 fixture.
// Expected dB values were calculated independently with double-precision RBJ transfer
// functions (https://github.com/jaakkopasanen/AutoEq/blob/master/autoeq/peq.py),
// not with EchoBiquadMath or the UI curve. Rows are 44.1 / 48 / 96 kHz.
private val profiles = listOf(
    OpraHeadphoneCorrectionPreset("autoeq:hd650", "hd650", "HD 650", "AutoEQ", "Auriculares Argentina", null, null, -6.4f,
        listOf(band("low_shelf", 105f, 7.1f, .70f), band("peak_dip", 105f, -3.1f, .39f),
            band("peak_dip", 8116f, 4.4f, 2.41f), band("peak_dip", 1994f, 2.2f, 3.11f),
            band("peak_dip", 1255f, -1.2f, 1.94f), band("high_shelf", 10000f, -10.3f, .70f),
            band("peak_dip", 3132f, -2.2f, 4.81f), band("peak_dip", 4034f, 1.1f, 5.08f),
            band("peak_dip", 10000f, 4.8f, 1.26f), band("peak_dip", 5928f, -1.7f, 6f))) to mapOf(
        44100 to listOf(-1.915867, -8.100695, -7.165672, -5.929883, -5.229673),
        48000 to listOf(-1.915871, -8.100605, -7.164269, -5.908960, -5.132376),
        96000 to listOf(-1.915884, -8.100221, -7.158284, -5.823910, -4.742476)),
    OpraHeadphoneCorrectionPreset("opra:hd650", "hd650", "HD 650", "Sennheiser", "AutoEQ", null, null, -6.4f,
        listOf(band("low_shelf", 105f, 6.2f, .7f), band("peak_dip", 21f, -8.8f, .44f),
            band("high_shelf", 10000f, -1.2f, .7f))) to mapOf(
        44100 to listOf(-5.545296, -6.541059, -6.421504, -6.455386, -7.000151),
        48000 to listOf(-5.545302, -6.541060, -6.421534, -6.460756, -7.000162),
        96000 to listOf(-5.545326, -6.541065, -6.421667, -6.484277, -7.000207)),
)
