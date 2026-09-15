package app.echo.android.model.playback

import kotlin.math.abs

object EchoParametricEq {
    const val MaxBands = 32
    const val MinFrequencyHz = 20f
    const val MaxFrequencyHz = 20_000f
    const val MinGainDb = -24f
    const val MaxGainDb = 18f
    const val MinQ = 0.1f
    const val MaxQ = 100f
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
        return sanitized
    }

    fun sanitizeBand(band: OpraEqBand): OpraEqBand? {
        val type = EchoEqFilterType.normalize(band.type)
        if (type !in AllowedTypes) return null
        if (!band.frequencyHz.isFinite() || !band.gainDb.isFinite()) return null
        val pass = isPass(type)
        val q = band.q?.takeIf { it.isFinite() && it > 0f }?.coerceIn(MinQ, MaxQ)
        return copySafe(
            band = band,
            type = type,
            pass = pass,
            q = if (pass) q else (q ?: 0.707f),
        )
    }

    private fun copySafe(band: OpraEqBand, type: String, pass: Boolean, q: Float?): OpraEqBand =
        OpraEqBand(
            type = type,
            frequencyHz = band.frequencyHz.coerceIn(MinFrequencyHz, MaxFrequencyHz),
            gainDb = band.gainDb.coerceIn(MinGainDb, MaxGainDb),
            q = q,
            slope = if (pass) snapSlope(band.slope) else null,
        )
}
