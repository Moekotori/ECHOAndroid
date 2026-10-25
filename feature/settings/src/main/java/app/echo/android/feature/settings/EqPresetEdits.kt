package app.echo.android.feature.settings

import app.echo.android.model.playback.EchoEqualizerUserPreset
import app.echo.android.model.playback.normalizedType
import kotlin.math.abs

/** Compare saved audio parameters, independent of names, source labels and runtime availability. */
internal fun EchoEqualizerUserPreset.hasEdits(current: EchoEqualizerUserPreset): Boolean {
    fun same(a: Float, b: Float) = a.isFinite() && b.isFinite() && abs(a - b) <= 0.001f
    fun sameNullable(a: Float?, b: Float?) = if (a == null || b == null) a == b else same(a, b)
    if (parametric != current.parametric || !same(preampDb, current.preampDb)) return true
    if (!parametric) return gainsDb.size != current.gainsDb.size ||
        gainsDb.indices.any { !same(gainsDb[it], current.gainsDb[it]) }
    return filters.size != current.filters.size || filters.indices.any { index ->
        val saved = filters[index]
        val edited = current.filters[index]
        saved.normalizedType() != edited.normalizedType() ||
            !same(saved.frequencyHz, edited.frequencyHz) || !same(saved.gainDb, edited.gainDb) ||
            !sameNullable(saved.q, edited.q) || !sameNullable(saved.slope, edited.slope)
    }
}
