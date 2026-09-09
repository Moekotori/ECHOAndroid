package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoSmartTransitionAnalysisMathTest {
    @Test
    fun silenceAndEnergyFromSyntheticMono() {
        val rate = EchoSmartTransitionPolicy.AnalysisSampleRateHz
        val samples = FloatArray(rate * 4)
        val lead = rate
        val trail = samples.size - rate
        for (index in samples.indices) {
            samples[index] = if (index < lead || index >= trail) 0f else 0.5f
        }
        val analysis = EchoSmartTransitionAnalysisMath.fromMono(samples, rate, 4_000)
        assertTrue(analysis.leadingSilenceMs >= 900)
        assertTrue(analysis.trailingSilenceMs >= 900)
        assertTrue(analysis.headEnergy < 0.35f)
        assertTrue(analysis.tailEnergy < 0.35f)
        val loud = EchoSmartTransitionAnalysisMath.fromMono(FloatArray(rate) { 0.5f }, rate, 1_000)
        assertEquals(0, loud.leadingSilenceMs)
        assertTrue(loud.headEnergy > 0.6f)
    }

    @Test
    fun downsampleStereoToAnalysisRateKeepsLengthRatio() {
        val input = FloatArray(48_000 * 2) { index -> if (index % 2 == 0) 0.5f else -0.5f }
        val mono = EchoSmartTransitionAnalysisMath.downsampleToMono(input, 48_000, 2, 12_000)
        assertEquals(12_000, mono.size)
        assertEquals(0f, mono[0], 0.0001f)
    }
}
