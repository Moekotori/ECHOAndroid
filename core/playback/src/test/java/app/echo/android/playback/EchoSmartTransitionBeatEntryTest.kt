package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EchoSmartTransitionBeatEntryTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun measured() = EchoSmartTransitionAnalysis(180_000, 400, 0, 0.4f, 0.4f,
        bpm = 120f, bpmConfidence = 0.8f, beatOffsetMs = 0, beatsMs = intArrayOf(0, 500, 1000))

    @Test fun measuredPhaseCanChooseNearbyBeatButWeakOrMissingPhaseCannot() {
        assertEquals(500, EchoSmartTransitionBeatEntry.select(measured()))
        for (unreliable in listOf(measured().copy(beatOffsetMs = null), measured().copy(bpmConfidence = 0.2f),
            measured().copy(bpmConfidence = Float.NaN), measured().copy(bpm = Float.POSITIVE_INFINITY),
            measured().copy(beatsMs = intArrayOf(0, 500, 510)), measured().copy(beatOffsetMs = 40))) {
            assertEquals(400, EchoSmartTransitionBeatEntry.select(unreliable))
        }
    }

    @Test fun protectsVocalAttackAndNeverSearchesFarIntoSong() {
        val vocal = EchoSmartTransitionVocal(FloatArray(9) { 0.9f }, 0.8f)
        assertEquals(400, EchoSmartTransitionBeatEntry.select(measured().copy(introVocal = vocal)))
        assertEquals(100, EchoSmartTransitionBeatEntry.select(measured().copy(leadingSilenceMs = 100)))
        assertEquals(3000, EchoSmartTransitionBeatEntry.select(measured().copy(leadingSilenceMs = 8000)))
    }

    @Test fun diskReloadDoesNotInventZeroPhaseAndRetainsMeasuredEntry() {
        val directory = temporary.newFolder()
        EchoSmartTransitionCache(directory).apply {
            put("missing-phase", measured().copy(beatOffsetMs = null))
            put("measured", measured())
            put("weak", measured().copy(bpmConfidence = 0.1f))
        }
        val cache = EchoSmartTransitionCache(directory)
        val missing = cache.get("missing-phase")!!
        assertTrue(missing.beatsMs.isEmpty())
        assertEquals(400, EchoSmartTransitionBeatEntry.select(missing))
        assertTrue(cache.get("weak")!!.beatsMs.isEmpty())
        assertEquals(500, EchoSmartTransitionBeatEntry.select(cache.get("measured")!!))
    }
}
