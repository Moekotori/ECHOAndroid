package app.echo.android.playback

import app.echo.android.model.playback.*
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test

class EchoPcEqRuntimeTest {
    @Test fun headphoneCalibrationDoesNotOverwriteThePersonalPreset() {
        val controller = EchoEqualizerController()
        controller.setEnabled(true); controller.setPreset(EchoEqualizerPreset.Bass)
        controller.setOptions(EchoEqOptions(60f, 2f))
        val personal = controller.state.value
        val bands = listOf(OpraEqBand("peak_dip",1000f,-4f,2f,null))
        val preset = OpraHeadphoneCorrectionPreset("calibration","model","Headphones","Brand","Author",null,null,-6f,bands)
        controller.applyOpraPreset(preset)
        val next = controller.state.value
        assertEquals(personal.presetId,next.presetId);assertEquals(personal.options,next.options);assertEquals(personal.bands,next.bands)
        assertEquals(bands,next.headphoneCorrection.filters)
        controller.setPreset(EchoEqualizerPreset.Vocal)
        assertEquals(bands,controller.state.value.headphoneCorrection.filters)
    }
    private class Backend : EchoPcEqBackend {
        override val available = true
        var creates = 0; var updates = 0
        val destroyed = mutableSetOf<Long>()
        var parameters = floatArrayOf()
        override fun create(rate: Int, channels: Int, configuration: FloatArray, preserve: Boolean): Long { parameters=configuration; return (++creates).toLong() }
        override fun update(handle: Long, configuration: FloatArray): Boolean { updates++; parameters=configuration; return true }
        override fun process(handle: Long, buffer: ByteBuffer, frames: Int, channels: Int, enabled: Boolean): Int {
            assertFalse(destroyed.contains(handle)); return if(enabled) 1 else 0
        }
        override fun reset(handle: Long) { assertFalse(destroyed.contains(handle)) }
        override fun destroy(handle: Long) { assertTrue(destroyed.add(handle)) }
    }
    @Test fun faderEditsReuseTheGraphAndDisabledEqReturnsToRawBypass() {
        val backend=Backend(); val jobs=mutableListOf<Runnable>(); val states=mutableListOf<EchoEqProcessingState>()
        val runtime=EchoPcEqRuntime({_,state->states+=state},backend,Executor{jobs+=it})
        fun drain() { while(jobs.isNotEmpty()) jobs.removeAt(0).run() }
        runtime.configure(48000,2)
        val filters=EchoEqualizerEngine.graphicFilters(List(31){if(it==30)6f else 0f})
        runtime.update(EchoEqualizerRuntime(true,filters=filters));drain()
        runtime.process(ByteBuffer.allocateDirect(64*8),64)
        assertEquals(31f,backend.parameters[5],0f)
        runtime.update(EchoEqualizerRuntime(true,filters=filters.map{it.copy(gainDb=it.gainDb*.5f)}))
        runtime.process(ByteBuffer.allocateDirect(64*8),64);drain()
        assertEquals(1,backend.creates);assertEquals(1,backend.updates)
        runtime.update(EchoEqualizerRuntime());runtime.process(ByteBuffer.allocateDirect(64*8),64)
        assertFalse(runtime.fading);assertEquals(EchoEqProcessingState.Idle,states.last())
        runtime.close();drain();assertEquals(setOf(1L),backend.destroyed)
    }
    @Test fun complexScopesUseTheFullMatrixForTheDrawnPeak() {
        val filters=listOf(OpraEqBand("peak_dip",1000f,6f,1f,null,scope=EchoEqBandScope.Left),
            OpraEqBand("peak_dip",1000f,-6f,1f,null,scope=EchoEqBandScope.Right))
        val curves=EchoEqChannelResponse.curves(filters,0f,48000f)
        assertTrue(curves.getValue(EchoEqBandScope.Stereo).maxOf{it.gainDb}>5.5f)
        assertTrue(curves.getValue(EchoEqBandScope.Right).minOf{it.gainDb}< -5.5f)
        val narrow=listOf(OpraEqBand("peak_dip",1000f,12f,100f,null))
        assertTrue(EchoEqChannelResponse.peakBoost(narrow,48000f)>11.9f)
    }
}
