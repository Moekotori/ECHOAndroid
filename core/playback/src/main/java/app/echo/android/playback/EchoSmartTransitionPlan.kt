package app.echo.android.playback

import kotlin.math.pow

internal enum class EchoSmartTransitionProfile {
    Crossfade,
    BeatCut,
    TailRide,
}

internal data class EchoSmartTransitionPlan(
    val overlapMs: Int,
    val nextStartMs: Int,
    val incomingGain: Float,
    val bassSwap: Boolean,
    val profile: EchoSmartTransitionProfile,
    val currentEndTrimMs: Int,
)

internal object EchoSmartTransitionPlanner {
    const val BeatCutMs = 60
    const val VocalShortenThreshold = 0.3f
    const val VocalCutThreshold = 0.45f
    private const val BassDensity = 0.55f
    private const val BassEnergy = 0.48f

    fun plan(
        current: EchoSmartTransitionAnalysis,
        next: EchoSmartTransitionAnalysis,
        remainingMs: Long,
        nextDurationMs: Long,
        maxOverlapMs: Int,
        currentReplayGainDb: Float?,
        nextReplayGainDb: Float?,
    ): EchoSmartTransitionPlan? {
        val density = ((current.tailEnergy + next.headEnergy) / 2f).coerceIn(0f, 1f)
        val conflict = EchoSmartTransitionVocalMath.conflict(current.outroVocal, next.introVocal)
        val tailRide = current.tailEnergy <= 0.52f && next.headEnergy <= 0.55f
        val nextStart = EchoSmartTransitionTempoMath.entryMs(
            leadingSilenceMs = next.leadingSilenceMs,
            beatsMs = if (next.bpmConfidence >= EchoSmartTransitionTempoMath.MinimumConfidence) next.beatsMs else intArrayOf(),
        )
        if (!current.hasOutro || !next.hasIntro || maxOverlapMs <= 0 ||
            !current.tailEnergy.isFinite() || !next.headEnergy.isFinite()) return null
        // Preserve the outgoing timeline and natural tail. Never end a mix before that stream ends.
        val trim = 0
        val usableRemaining = remainingMs.coerceAtLeast(0L)
        val highEnergy = density >= BassDensity && current.tailEnergy >= BassEnergy && next.headEnergy >= BassEnergy
        var overlap = EchoSmartTransitionPolicy.overlapMs(
            tailEnergy = current.tailEnergy,
            headEnergy = next.headEnergy,
            remainingMs = usableRemaining,
            nextDurationMs = nextDurationMs,
            maxMs = maxOverlapMs,
        ) ?: return null
        if (conflict >= VocalShortenThreshold) {
            // A centre-channel heuristic alone does not justify a 60 ms "beat cut".
            // Use a short smooth fade, bounded by the user's cap and available PCM budget.
            overlap = minOf(overlap, if (conflict >= VocalCutThreshold) 500 else 1000)
        }
        val profile = if (tailRide) EchoSmartTransitionProfile.TailRide else EchoSmartTransitionProfile.Crossfade
        return EchoSmartTransitionPlan(
            overlapMs = overlap,
            nextStartMs = nextStart,
            incomingGain = incomingGain(currentReplayGainDb, nextReplayGainDb),
            bassSwap = highEnergy && profile != EchoSmartTransitionProfile.BeatCut,
            profile = profile,
            currentEndTrimMs = trim,
        )
    }

    fun incomingGain(currentReplayGainDb: Float?, nextReplayGainDb: Float?): Float {
        if (currentReplayGainDb == null || nextReplayGainDb == null || !currentReplayGainDb.isFinite() || !nextReplayGainDb.isFinite()) return 1f
        val db = ((nextReplayGainDb - currentReplayGainDb) * 0.55f).coerceIn(-3.5f, 3f)
        return 10.0.pow((db / 20.0)).toFloat().coerceIn(0.4f, 1.4f)
    }
}
