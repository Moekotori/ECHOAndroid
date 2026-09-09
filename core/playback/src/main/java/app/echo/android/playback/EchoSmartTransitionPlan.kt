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
        val trim = current.trailingSilenceMs.coerceIn(0, 8_000)
        val usableRemaining = (remainingMs - trim).coerceAtLeast(0L)
        val highEnergy = density >= BassDensity && current.tailEnergy >= BassEnergy && next.headEnergy >= BassEnergy
        if (conflict >= VocalCutThreshold && highEnergy) {
            return EchoSmartTransitionPlan(
                overlapMs = BeatCutMs,
                nextStartMs = nextStart,
                incomingGain = incomingGain(currentReplayGainDb, nextReplayGainDb),
                bassSwap = false,
                profile = EchoSmartTransitionProfile.BeatCut,
                currentEndTrimMs = trim,
            )
        }
        var overlap = EchoSmartTransitionPolicy.overlapMs(
            tailEnergy = current.tailEnergy,
            headEnergy = next.headEnergy,
            remainingMs = usableRemaining,
            nextDurationMs = nextDurationMs,
            maxMs = maxOverlapMs,
        ) ?: return null
        if (conflict >= VocalShortenThreshold) {
            val beatMs = next.bpm?.takeIf { next.bpmConfidence >= EchoSmartTransitionTempoMath.MinimumConfidence }
                ?.let { (60_000f / it).toInt() } ?: 500
            overlap = overlap.coerceAtMost(beatMs.coerceIn(400, 1_500))
            if (overlap < EchoSmartTransitionPolicy.MinOverlapMs && usableRemaining >= BeatCutMs) {
                return EchoSmartTransitionPlan(
                    overlapMs = BeatCutMs,
                    nextStartMs = nextStart,
                    incomingGain = incomingGain(currentReplayGainDb, nextReplayGainDb),
                    bassSwap = false,
                    profile = EchoSmartTransitionProfile.BeatCut,
                    currentEndTrimMs = trim,
                )
            }
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
        if (currentReplayGainDb == null || nextReplayGainDb == null) return 1f
        val db = ((currentReplayGainDb - nextReplayGainDb) * 0.55f).coerceIn(-3.5f, 3f)
        return 10.0.pow((db / 20.0)).toFloat().coerceIn(0.4f, 1.4f)
    }
}
