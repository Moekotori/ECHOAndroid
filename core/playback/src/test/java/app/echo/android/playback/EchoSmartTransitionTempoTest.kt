package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EchoSmartTransitionTempoTest {
    @Test
    fun clickTrackNearOneTwentyBpm() {
        val rate = EchoSmartTransitionPolicy.AnalysisSampleRateHz
        val hop = maxOf(1, (rate * 0.01f).toInt())
        val samples = FloatArray(rate * 8)
        val period = rate / 2
        var start = 0
        while (start < samples.size) {
            val end = minOf(samples.size, start + hop)
            for (index in start until end) samples[index] = 1f
            start += period
        }
        val tempo = EchoSmartTransitionTempoMath.analyze(samples, rate, 0)
        assertTrue("bpm=${tempo.bpm} conf=${tempo.confidence}", tempo.bpm != null && abs(tempo.bpm - 120f) < 12f)
        val entry = EchoSmartTransitionTempoMath.entryMs(200, tempo.beatsMs)
        assertTrue(entry in 0..200)
    }

    @Test
    fun halfTimePairFoldsTowardUnity() {
        val ratio = EchoSmartTransitionTempoMath.tempoRatio(85f, 170f)
        assertEquals(1f, ratio, 0.02f)
    }

    @Test
    fun entryNeverStartsBeforeSilence() {
        val beats = intArrayOf(0, 500, 1000, 1500)
        assertEquals(500, EchoSmartTransitionTempoMath.entryMs(500, beats))
        assertEquals(800, EchoSmartTransitionTempoMath.entryMs(800, intArrayOf()))
    }
}
