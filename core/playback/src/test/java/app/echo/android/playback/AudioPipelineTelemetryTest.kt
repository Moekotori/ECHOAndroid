package app.echo.android.playback

import app.echo.android.model.playback.*
import org.junit.Assert.*
import org.junit.Test

class AudioPipelineTelemetryTest {
    @Test fun oldConverterResetDoesNotEraseNewPipelineFacts() {
        val pipeline = EchoAudioPipelineState()
        val old = Any(); val next = Any()
        val pcm = EchoPcmReadout(48000, 24, 2)
        val a = EchoUsbConversionDiagnostics(pcm, pcm)
        val b = EchoUsbConversionDiagnostics(pcm, pcm.copy(bitDepth = 32))
        pipeline.conversion(old, a)
        pipeline.conversion(next, b)
        pipeline.conversion(old, null)
        assertEquals(b, pipeline.diagnostics.value.usbConversion)
        pipeline.conversion(next, null)
        assertNull(pipeline.diagnostics.value.usbConversion)
    }
}
