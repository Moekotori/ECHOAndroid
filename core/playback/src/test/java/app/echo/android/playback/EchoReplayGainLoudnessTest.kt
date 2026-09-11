package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class EchoReplayGainLoudnessTest {
    @Test
    fun quieterSineGetsHigherGainThanFullScale() {
        val loud = sine(amplitude = 1f)
        val quiet = sine(amplitude = 0.1f)
        val loudGain = EchoReplayGainLoudness.gainDbFromMono48k(loud)
        val quietGain = EchoReplayGainLoudness.gainDbFromMono48k(quiet)
        assertNotNull(loudGain)
        assertNotNull(quietGain)
        assertTrue(quietGain!! > loudGain!! + 10f)
        assertTrue(loudGain in -15f..15f)
        assertTrue(quietGain in -15f..15f)
    }

    @Test
    fun silenceClampsToMaximumBoost() {
        val silence = FloatArray(EchoReplayGainLoudness.TargetRateHz) { 0f }
        assertEquals(15f, EchoReplayGainLoudness.gainDbFromMono48k(silence)!!, 0.01f)
    }

    @Test
    fun gainTagUsesSignedDb() {
        assertEquals("-6.50 dB", EchoReplayGainLoudness.formatGainTag(-6.5f))
        assertEquals("+3.00 dB", EchoReplayGainLoudness.formatGainTag(3f))
    }

    private fun sine(amplitude: Float): FloatArray {
        val rate = EchoReplayGainLoudness.TargetRateHz
        return FloatArray(rate) { index ->
            (amplitude * sin(2.0 * PI * 1000.0 * index / rate)).toFloat()
        }
    }
}
