package app.echo.android.data

import java.time.YearMonth

/** Use each event's recorded local date/hour, matching the existing history semantics. */
internal data class MonthlyRecapPeriod(val fromDay: Long, val untilDay: Long) {
    companion object {
        fun of(year: Int, month: Int): MonthlyRecapPeriod {
            require(year in 1970..9999)
            val selected = YearMonth.of(year, month)
            return MonthlyRecapPeriod(selected.atDay(1).toEpochDay(), selected.plusMonths(1).atDay(1).toEpochDay())
        }
    }
}
