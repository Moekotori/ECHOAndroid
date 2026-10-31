package app.echo.android.feature.library

import java.math.BigDecimal
import java.math.RoundingMode

private val FilterInteger = Regex("[0-9]{1,40}")
private val FilterDecimal = Regex("(?:[0-9]{1,40}(?:\\.[0-9]{0,40})?|\\.[0-9]{1,40})")
private val FilterRangeScales = listOf(1L, 1L, 1_000L, 1_000L, 1_048_576L, 1_048_576L, 1L, 1L)

/** Exact decimal units: reopening a filter must never truncate milliseconds or bytes. */
internal fun formatLibraryFilterUnit(value: Long?, scale: Long): String = value?.let {
    BigDecimal.valueOf(it).divide(BigDecimal.valueOf(scale)).stripTrailingZeros().toPlainString()
}.orEmpty()

/** A null list means invalid input; null elements mean an unlimited bound. */
internal fun parseLibraryFilterRanges(ranges: List<String>): List<Long?>? {
    if (ranges.size != FilterRangeScales.size) return null
    return ranges.mapIndexed { index, raw ->
        val text = raw.trim().replace(',', '.')
        if (text.isEmpty()) return@mapIndexed null
        val scale = FilterRangeScales[index]
        if (text.length > 40 || !(if (scale == 1L) FilterInteger else FilterDecimal).matches(text)) return null
        try {
            // Stored durations/sizes are integers: ceil a lower bound and floor an upper
            // bound to preserve decimal comparisons without dropping matching rows.
            BigDecimal(text).multiply(BigDecimal.valueOf(scale))
                .setScale(0, if (index % 2 == 0) RoundingMode.CEILING else RoundingMode.FLOOR)
                .longValueExact()
        } catch (_: ArithmeticException) {
            return null
        }
    }
}
