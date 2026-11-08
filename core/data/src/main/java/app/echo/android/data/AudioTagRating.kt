package app.echo.android.data

import kotlin.math.roundToInt

/** Read-only import of common POPM, integer-star, percentage and FMPS conventions. */
internal object AudioTagRating {
    fun popm(payload: ByteArray): Int? {
        val end = payload.indexOf(0)
        if (end < 0 || end + 1 >= payload.size) return null
        return when (val value = payload[end + 1].toInt() and 255) {
            0 -> 0
            in 1..31 -> 1
            in 32..95 -> 2
            in 96..159 -> 3
            in 160..223 -> 4
            else -> 5
        }
    }
    fun fields(values: Map<String, String>): Int? {
        values["FMPS_RATING"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..1.0 }
            ?.let { return (it * 5).roundToInt() }
        val value = (values["RATING"] ?: values["RATING WMP"])?.trim()?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it in 0.0..100.0 } ?: return null
        return (if (value <= 5) value else value / 20).roundToInt().coerceIn(0, 5)
    }
}
