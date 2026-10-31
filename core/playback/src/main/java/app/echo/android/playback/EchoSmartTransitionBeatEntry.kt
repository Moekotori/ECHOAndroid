package app.echo.android.playback

import kotlin.math.abs

/** BPM alone does not locate beats. Only a measured, consistent phase can move the entry. */
internal object EchoSmartTransitionBeatEntry {
    fun select(analysis: EchoSmartTransitionAnalysis): Int {
        val silence = analysis.leadingSilenceMs.coerceIn(0, EchoSmartTransitionPolicy.MaxSilenceSkipMs)
        val bpm = analysis.bpm ?: return silence
        val offset = analysis.beatOffsetMs ?: return silence
        val confidence = analysis.bpmConfidence
        if (!bpm.isFinite() || bpm !in 80f..180f || !confidence.isFinite() ||
            confidence !in EchoSmartTransitionTempoMath.MinimumConfidence..1f) return silence
        val period = 60_000f / bpm
        if (offset < 0 || offset >= period || analysis.beatsMs.size !in 3..64) return silence
        // These are measured-window beat grids, not arbitrary positions from another song/window.
        val beats = analysis.beatsMs
        if (beats[0] != offset) return silence
        for (index in 1 until beats.size) {
            val interval = beats[index].toLong() - beats[index - 1]
            if (abs(interval - period) > maxOf(20f, period * 0.08f)) return silence
        }
        val vocal = analysis.introVocal
        if (vocal.usable && vocal.curve.firstOrNull()?.let { it.isFinite() && it >= 0.3f } == true) return silence
        return EchoSmartTransitionTempoMath.entryMs(silence, beats)
    }
}
