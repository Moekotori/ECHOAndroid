package app.echo.android.playback

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class EchoSmartTransitionVocalTest {
    @Test
    fun centredPulsesConflictMoreThanSideSignal() {
        val rate = 11_025
        val centre = stereo(rate, centred = true)
        val side = stereo(rate, centred = false)
        val centreVocal = EchoSmartTransitionVocalMath.fromStereo(centre, rate)
        val sideVocal = EchoSmartTransitionVocalMath.fromStereo(side, rate)
        val high = EchoSmartTransitionVocalMath.conflict(centreVocal, centreVocal)
        val low = EchoSmartTransitionVocalMath.conflict(centreVocal, sideVocal)
        assertTrue("centre=$high side=$low conf=${centreVocal.confidence}", high >= low)
    }

    private fun stereo(rate: Int, centred: Boolean): FloatArray {
        val frames = rate * 3
        val samples = FloatArray(frames * 2)
        for (frame in 0 until frames) {
            val env = if (frame % (rate / 2) < rate / 4) 0.45f else 0.03f
            val sample = env * sin(2.0 * Math.PI * 1_000.0 * frame / rate).toFloat()
            samples[frame * 2] = sample
            samples[frame * 2 + 1] = if (centred) sample else -sample
        }
        return samples
    }
}
