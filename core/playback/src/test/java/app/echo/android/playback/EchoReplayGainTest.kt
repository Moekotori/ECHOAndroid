package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class EchoReplayGainTest {
    @Test
    fun positiveGainUsesEnhancerInsteadOfClampedPlayerVolume() {
        val output = echoReplayGainOutput(
            enabled = true,
            preampDb = 6f,
            trackGainDb = 0f,
        )
        val legacyVolume = 10.0.pow(6.0 / 20.0).toFloat().coerceIn(0.25f, 1.4f)

        assertEquals(1f, output.playerVolume, 0.001f)
        assertTrue(output.enhancerGainMb > 0)
        assertTrue(output.playerVolume != legacyVolume)
        assertEquals(1.4f, legacyVolume, 0.001f)
    }

    @Test
    fun disabledGainLeavesUnityVolumeWithoutEnhancer() {
        val output = echoReplayGainOutput(
            enabled = false,
            preampDb = 6f,
            trackGainDb = 3f,
        )
        assertEquals(1f, output.playerVolume, 0.001f)
        assertEquals(0, output.enhancerGainMb)
    }
}
