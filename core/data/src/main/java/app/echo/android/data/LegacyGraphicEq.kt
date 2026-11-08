package app.echo.android.data

import app.echo.android.model.playback.*
import kotlin.math.abs

/** Pure compatibility read: preserve the five original filters, never fit a new audible curve. */
internal object LegacyGraphicEq {
    fun filters(presetId: String?, gains: String?, parametric: Boolean): List<OpraEqBand> {
        if (parametric) return emptyList()
        val parsed = gains?.split(',')?.mapNotNull { it.toFloatOrNull()?.takeIf(Float::isFinite) }.orEmpty()
        if (parsed.isNotEmpty() && parsed.size != 5) return emptyList()
        val legacy = if (presetId != EchoEqualizerPreset.Custom) EchoEqualizerPresets.legacyGains(presetId.orEmpty()) ?: parsed else parsed
        if (legacy.size != 5 || legacy.none { abs(it) >= .05f }) return emptyList()
        return EchoEqualizerPresets.legacyFilters(legacy)
    }
}
