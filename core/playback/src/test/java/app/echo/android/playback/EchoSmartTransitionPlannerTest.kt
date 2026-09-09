package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoSmartTransitionPlannerTest {
    @Test
    fun loudnessClampsAndNeutralWithoutReplayGain() {
        assertEquals(1f, EchoSmartTransitionPlanner.incomingGain(null, -6f), 0.001f)
        val quieterNext = EchoSmartTransitionPlanner.incomingGain(-8f, -2f)
        assertTrue(quieterNext < 1f)
        val louderNext = EchoSmartTransitionPlanner.incomingGain(-2f, -8f)
        assertTrue(louderNext > 1f)
        assertTrue(EchoSmartTransitionPlanner.incomingGain(-20f, 20f) <= 1.4f)
    }

    @Test
    fun highEnergyEnablesBassSwapAndVocalCutUsesBeatCut() {
        val energetic = analysis(tail = 0.7f, head = 0.7f)
        val next = analysis(tail = 0.7f, head = 0.7f)
        val fade = EchoSmartTransitionPlanner.plan(energetic, next, 20_000, 180_000, 4_000, null, null)
        assertTrue(fade!!.bassSwap)
        assertEquals(EchoSmartTransitionProfile.Crossfade, fade.profile)

        val vocal = EchoSmartTransitionVocal(FloatArray(9) { 0.9f }, 0.8f)
        val currentVocal = energetic.copy(outroVocal = vocal)
        val nextVocal = next.copy(introVocal = vocal)
        val cut = EchoSmartTransitionPlanner.plan(currentVocal, nextVocal, 20_000, 180_000, 4_000, null, null)
        assertEquals(EchoSmartTransitionProfile.BeatCut, cut!!.profile)
        assertTrue(cut.overlapMs in 40..80)
        assertFalse(cut.bassSwap)
    }

    @Test
    fun beatEntryUsesSilenceFloor() {
        val next = analysis(tail = 0.4f, head = 0.4f).copy(
            leadingSilenceMs = 400,
            bpm = 120f,
            bpmConfidence = 0.8f,
            beatsMs = intArrayOf(0, 500, 1000),
        )
        val plan = EchoSmartTransitionPlanner.plan(analysis(0.4f, 0.4f), next, 20_000, 180_000, 4_000, null, null)
        assertTrue(plan!!.nextStartMs <= 400)
    }

    private fun analysis(tail: Float, head: Float) = EchoSmartTransitionAnalysis(
        durationMs = 180_000,
        leadingSilenceMs = 0,
        trailingSilenceMs = 0,
        headEnergy = head,
        tailEnergy = tail,
    )
}
