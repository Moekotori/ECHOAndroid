package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.EchoEqualizerPresetDefinition
import app.echo.android.model.playback.EchoEqualizerPresets
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Signal-level acceptance for every shipped preset; this does not replace headphone listening. */
@UnstableApi
@RunWith(Parameterized::class)
class EchoEqualizerPresetAcceptanceTest(private val preset: EchoEqualizerPresetDefinition) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun presets() = EchoEqualizerPresets.presets.map { arrayOf(it) }
    }

    private val filters get() = EchoEqualizerEngine.graphicFilters(preset.gainsDb)

    @Test fun quietTonesMatchFrequencyResponseAndDoNotLeakBetweenChannels() {
        for (rate in listOf(44100, 48000, 96000)) {
            for (frequency in EchoEqualizerPresets.defaultFrequenciesHz) {
                val samples = render(rate, frequency.toDouble(), 0.05f, 0f)
                var power = 0.0
                var count = 0
                for (frame in rate / 5 until samples.size / 2) {
                    val left = samples[frame * 2]
                    assertTrue("${preset.id}: non-finite sample", left.isFinite())
                    assertEquals("${preset.id}: channel leakage", 0f, samples[frame * 2 + 1], 0f)
                    power += left * left
                    count++
                }
                val measured = 20 * log10(sqrt(power / count) / (0.05 / sqrt(2.0)))
                val expected = EchoBiquadMath.sampleCurveDb(filters, frequency.toFloat(), rate.toFloat(), 0f)
                assertEquals("${preset.id} $rate Hz / $frequency Hz", expected.toDouble(), measured, 0.1)
            }
        }
    }

    @Test fun defaultPresetDoesNotClipNearFullScaleTone() {
        checkHeadroom(compensate = false)
    }

    @Test fun recommendedHeadroomDoesNotClipSameTone() {
        checkHeadroom(compensate = true)
    }

    @Test fun selectedPresetHeadroomSurvivesRestorationAndManualAdjustment() {
        val controller = EchoEqualizerController()
        controller.setPreset(preset.id)
        val selected = controller.state.value
        assertEquals(selected.suggestedPreampDb, selected.preampDb, 0.001f)
        val restored = EchoEqualizerController()
        restored.setConfig(false, selected.presetId, selected.gainsDb, selected.preampDb)
        assertEquals(selected.preampDb, restored.state.value.preampDb, 0.001f)
        assertEquals(selected.responseCurve, restored.state.value.responseCurve)
        restored.setPreamp(-8f)
        restored.setConfig(false, selected.presetId, selected.gainsDb, -8f)
        assertEquals(-8f, restored.state.value.preampDb, 0.001f)
        restored.setBandGain(0, 1f)
        assertEquals(-8f, restored.state.value.preampDb, 0.001f)
        restored.setPreset(preset.id)
        assertEquals(selected.preampDb, restored.state.value.preampDb, 0.001f)
        restored.reset()
        assertEquals(0f, restored.state.value.preampDb, 0.001f)
    }

    private fun checkHeadroom(compensate: Boolean) {
        // Use the same preset selection entry point as the UI, including automatic headroom.
        val controller = EchoEqualizerController()
        controller.setPreset(preset.id)
        val state = controller.state.value
        val preamp = if (compensate) state.suggestedPreampDb else state.preampDb
        val failures = mutableListOf<String>()
        for (rate in listOf(44100, 48000, 96000)) {
            val peak = EchoEqualizerEngine.responseCurve(filters, sampleRateHz = rate.toFloat()).maxBy { it.gainDb }
            val samples = render(rate, peak.frequencyHz.toDouble(), 0.98f, preamp)
            val steady = samples.asSequence().filterIndexed { index, _ -> index % 2 == 0 && index >= rate / 5 * 2 }.toList()
            val clipped = steady.count { abs(it) >= 0.99999f }
            val percent = clipped * 100.0 / steady.size
            println("PRESET ${preset.id} rate=$rate preamp=$preamp boost=${peak.gainDb} frequency=${peak.frequencyHz} clipped=$clipped/${steady.size} ($percent%) compensated=$compensate")
            if (clipped > 0) failures += "$rate Hz: $clipped samples clipped ($percent%)"
        }
        assertTrue("${preset.id}, preamp $preamp dB: ${failures.joinToString()}", failures.isEmpty())
    }

    private fun render(rate: Int, frequency: Double, amplitude: Float, preamp: Float): FloatArray {
        val runtime = EchoEqualizerRuntime(true, preamp, filters)
        val frames = rate * 2 / 5
        val input = ByteBuffer.allocateDirect(frames * 8).order(ByteOrder.nativeOrder())
        repeat(frames) { frame ->
            input.putFloat((amplitude * sin(2 * PI * frequency * frame / rate)).toFloat())
            input.putFloat(0f)
        }
        input.flip()
        // Flat is intentionally excluded from Media3's processing chain.
        if (!runtime.shouldProcess) return FloatArray(frames * 2).also { input.asFloatBuffer().get(it) }
        val processor = EchoEqualizerAudioProcessor()
        processor.setRuntime(runtime)
        processor.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_FLOAT))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder()).asFloatBuffer()
        assertEquals(frames * 2, output.remaining())
        return FloatArray(frames * 2).also { output.get(it); processor.reset() }
    }
}
