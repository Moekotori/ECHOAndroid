package app.echo.android.playback

import app.echo.android.model.playback.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class EchoDynamicsCompressorTest {
    private fun settled(mode: EchoCompressorMode, level: Float): Float {
        val compressor = EchoDynamicsCompressor().apply { configure(48000); setMode(mode) }
        var gain = 1f
        repeat(24000) { gain = compressor.gain(level) }
        return gain
    }

    @Test fun nightReducesLoudQuietDifferenceMoreThanGentleAndOffIsUnity() {
        assertEquals(1f, settled(EchoCompressorMode.Off, 0.8f), 0f)
        val gentleRatio = settled(EchoCompressorMode.Gentle, 0.8f) / settled(EchoCompressorMode.Gentle, 0.02f)
        val nightRatio = settled(EchoCompressorMode.Night, 0.8f) / settled(EchoCompressorMode.Night, 0.02f)
        assertTrue(gentleRatio < 0.7f)
        assertTrue(nightRatio < gentleRatio)
        assertTrue(settled(EchoCompressorMode.Night, 0.02f) <= 1.585f)
    }

    @Test fun switchingOffRecoversSmoothly() {
        val compressor = EchoDynamicsCompressor().apply { configure(48000); setMode(EchoCompressorMode.Night) }
        repeat(24000) { compressor.gain(0.8f) }
        val before = compressor.gain(0.8f)
        compressor.setMode(EchoCompressorMode.Off)
        val first = compressor.gain(0.8f)
        assertTrue(first >= before && first - before < 0.01f)
        repeat(24000) { compressor.gain(0.8f) }
        assertTrue(compressor.neutral)
    }

    @Test fun linkedChannelsRetainBalanceAndProtectBurstsEvenWithoutOptionalLimiter() {
        val kernel = EchoDspKernel().apply {
            configure(48000)
            setTarget(EchoDspSettings(compressor = EchoCompressorMode.Night), 0f)
        }
        repeat(24000) { index ->
            val sample = if (index % 4000 < 200) 2f else 0.02f
            kernel.process(sample, sample * 0.25f, true)
            assertTrue(abs(kernel.left) <= 0.891251f)
            assertEquals(kernel.left * 0.25f, kernel.right, 0.00001f)
        }
    }
}
