package app.echo.android.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthlyRecapPeriodTest {
    @Test fun calendarMonthHasExclusiveNextMonthBoundary() {
        val period = MonthlyRecapPeriod.of(2026, 9)
        assertEquals(LocalDate.of(2026, 9, 1).toEpochDay(), period.fromDay)
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay(), period.untilDay)
        assertEquals(30, period.untilDay - period.fromDay)
    }

    @Test fun leapFebruaryAndDecemberUseCalendarLengths() {
        val leap = MonthlyRecapPeriod.of(2024, 2)
        assertEquals(29, leap.untilDay - leap.fromDay)
        assertEquals(LocalDate.of(2027, 1, 1).toEpochDay(), MonthlyRecapPeriod.of(2026, 12).untilDay)
    }
}
