package app.echo.android

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoCalendarVersionTest {
    private val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))

    @Test
    fun versionNameMatchesBuildDay() {
        val expected = "${today.year % 100}.${today.monthValue}.${today.dayOfMonth}"
        assertEquals(expected, BuildConfig.VERSION_NAME)
    }

    @Test
    fun versionCodeEncodesBuildDay() {
        val expected = (today.year % 100) * 10_000 + today.monthValue * 100 + today.dayOfMonth
        assertEquals(expected, BuildConfig.VERSION_CODE)
    }

    @Test
    fun versionNameIsUnpaddedYearMonthDay() {
        assertTrue(BuildConfig.VERSION_NAME.matches(Regex("""\d{1,2}\.\d{1,2}\.\d{1,2}""")))
    }
}
