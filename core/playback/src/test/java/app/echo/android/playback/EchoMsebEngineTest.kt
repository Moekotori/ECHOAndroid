package app.echo.android.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

@UnstableApi
class EchoMsebEngineTest {
    @Test fun neutralAndDisabledBypassAndEveryControlShapesSound() {
        assertTrue(EchoMsebEngine.filters(EchoMsebState(enabled = true)).isEmpty())
        assertTrue(EchoMsebEngine.filters(EchoMsebPreset.Bass.applyTo(EchoMsebState())).isEmpty())
        EchoMsebParameter.entries.forEach { parameter ->
            val subtle = EchoEqualizerController()
            subtle.setMseb(EchoMsebState(true).withValue(parameter, 1f))
            assertTrue("Small $parameter adjustment must remain audible", subtle.processingRuntime().shouldProcess)
            for (direction in listOf(-100f, 100f)) {
                val filters = EchoMsebEngine.filters(EchoMsebState(true).withValue(parameter, direction))
                assertTrue("$parameter / $direction", filters.isNotEmpty())
                for (rate in listOf(22050f, 44100f, 48000f, 96000f, 192000f)) {
                    assertTrue(EchoEqualizerEngine.responseCurve(filters, sampleRateHz = rate).all { it.gainDb.isFinite() })
                }
            }
        }
        assertTrue(EchoMsebEngine.filters(EchoMsebState(true, List(10) { 100f })).size <= 11)
    }

    @Test fun independentSwitchesComposeWithEqAndKeepItsSettings() {
        val controller = EchoEqualizerController()
        controller.setConfig(true, EchoEqualizerPreset.Bass, emptyList(), -4f)
        val eqOnly = controller.processingRuntime()
        val mseb = EchoMsebState(true).withValue(EchoMsebParameter.BassExtension, 100f)
        controller.setMseb(mseb)
        val combined = controller.processingRuntime()
        assertEquals(eqOnly.filters.size + 1, combined.filters.size)
        assertTrue(combined.preampDb <= -4f)
        assertTrue(EchoEqualizerEngine.responseCurve(combined.filters, combined.preampDb).maxOf { it.gainDb } < 0f)
        assertEquals(-4f, controller.state.value.preampDb, 0.001f)
        controller.setEnabled(false)
        assertTrue(controller.processingRuntime().shouldProcess)
        assertEquals(1, controller.processingRuntime().filters.size)
        controller.setMseb(mseb.copy(enabled = false))
        assertFalse(controller.processingRuntime().shouldProcess)
        controller.setEnabled(true)
        assertEquals(eqOnly, controller.processingRuntime())
        assertEquals(100f, controller.state.value.mseb.value(EchoMsebParameter.BassExtension), 0f)
    }

    @Test fun bassExtensionChangesActualPcmAndMatchesItsResponse() {
        val controller = EchoEqualizerController()
        controller.setMseb(EchoMsebState(true).withValue(EchoMsebParameter.BassExtension, 100f))
        val runtime = controller.processingRuntime()
        val processor = controller.processor
        processor.configure(AudioProcessor.AudioFormat(48000, 1, C.ENCODING_PCM_FLOAT))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val input = ByteBuffer.allocateDirect(24000 * 4).order(ByteOrder.nativeOrder())
        repeat(24000) { input.putFloat((0.1 * sin(2 * PI * 50 * it / 48000)).toFloat()) }
        input.flip()
        processor.queueInput(input)
        val output = processor.output.order(ByteOrder.nativeOrder()).asFloatBuffer()
        var power = 0.0
        repeat(24000) { index ->
            val value = output.get()
            assertTrue(value.isFinite())
            if (index >= 12000) power += value * value
        }
        val actualDb = 20 * log10(sqrt(power / 12000) / (0.1 / sqrt(2.0)))
        val expectedDb = EchoBiquadMath.sampleCurveDb(runtime.filters, 50f, 48000f, runtime.preampDb)
        assertEquals(expectedDb.toDouble(), actualDb, 0.05)
        assertTrue(actualDb < -0.5)
        assertEquals(48000, controller.state.value.processingSampleRateHz)
    }
}
