package app.echo.android.model.playback

import kotlin.math.abs

object EchoParametricEq {
    const val MaxBands = 32
    const val EditorMaxBands = 31
    const val MaxDynamicBands = 4
    const val EditorMaxGainDb = 12f
    const val EditorMaxQ = 12f
    const val NotchMaxQ = 24f
    const val MinFrequencyHz = 20f
    const val MaxFrequencyHz = 20_000f
    const val MinGainDb = -24f
    const val MaxGainDb = 30f // PC headphone-correction/legacy compatibility; editor gain remains +12 dB.
    const val MinQ = 0.1f
    const val MaxQ = 1000f
    val PassSlopesDb: List<Float> = listOf(6f, 12f, 18f, 24f, 30f, 36f)
    val AllowedTypes: Set<String> = setOf(
        EchoEqFilterType.PeakDip,
        EchoEqFilterType.LowShelf,
        EchoEqFilterType.HighShelf,
        EchoEqFilterType.LowPass,
        EchoEqFilterType.HighPass,
        EchoEqFilterType.BandStop,
        EchoEqFilterType.BandPass,
    )

    fun isPass(type: String): Boolean {
        val normalized = EchoEqFilterType.normalize(type)
        return normalized == EchoEqFilterType.LowPass || normalized == EchoEqFilterType.HighPass
    }

    fun snapSlope(raw: Float?): Float {
        val value = raw?.takeIf { it.isFinite() } ?: 12f
        return PassSlopesDb.minBy { abs(it - value) }
    }

    fun sanitize(filters: List<OpraEqBand>): List<OpraEqBand>? {
        if (filters.size !in 1..MaxBands) return null
        val sanitized = filters.map { sanitizeBand(it) ?: return null }
        if (sanitized.count { it.enabled && it.dynamicMode != EchoEqDynamicMode.Off } > MaxDynamicBands) return null
        return sanitized
    }

    fun sanitizeBand(band: OpraEqBand): OpraEqBand? {
        val type = EchoEqFilterType.normalize(band.type)
        if (type !in AllowedTypes) return null
        if (!band.frequencyHz.isFinite() || !band.gainDb.isFinite()) return null
        val pass = isPass(type)
        val q = band.q?.takeIf { it.isFinite() && it > 0f }?.coerceIn(0.001f, MaxQ)
        if (band.dynamicMode != EchoEqDynamicMode.Off && type !in EchoEqOptions.GainTypes) return null
        if (band.dynamicMode != EchoEqDynamicMode.Off && (band.frequencyHz < 20 || band.gainDb !in -24f..12f || (q ?: 1f) !in .1f..12f)) return null
        return copySafe(
            band = band,
            type = type,
            pass = pass,
            q = q ?: if (pass) 0.707f else 1f,
        )
    }

    private fun copySafe(band: OpraEqBand, type: String, pass: Boolean, q: Float?): OpraEqBand =
        band.copy(
            type = type,
            frequencyHz = band.frequencyHz.coerceIn(1f, MaxFrequencyHz),
            gainDb = band.gainDb.coerceIn(-60f, MaxGainDb),
            q = q,
            slope = if (pass) snapSlope(band.slope) else null,
            dynamicThresholdDb = band.dynamicThresholdDb.takeIf(Float::isFinite)?.coerceIn(-48f, 0f) ?: -24f,
            dynamicRatio = band.dynamicRatio.takeIf(Float::isFinite)?.coerceIn(1f, 20f) ?: 2f,
            dynamicAttackMs = band.dynamicAttackMs.takeIf(Float::isFinite)?.coerceIn(0.5f, 80f) ?: 5f,
            dynamicReleaseMs = band.dynamicReleaseMs.takeIf(Float::isFinite)?.coerceIn(10f, 400f) ?: 80f,
        )
}
