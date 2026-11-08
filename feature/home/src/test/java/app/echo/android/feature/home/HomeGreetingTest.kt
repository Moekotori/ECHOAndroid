package app.echo.android.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGreetingTest {
    @Test
    fun everyHourMapsToExpectedPeriod() {
        val expected = mapOf(
            0 to R.string.home_greeting_night, 4 to R.string.home_greeting_night,
            5 to R.string.home_greeting_morning, 10 to R.string.home_greeting_morning,
            11 to R.string.home_greeting_noon, 13 to R.string.home_greeting_noon,
            14 to R.string.home_greeting_afternoon, 17 to R.string.home_greeting_afternoon,
            18 to R.string.home_greeting_evening, 22 to R.string.home_greeting_evening,
            23 to R.string.home_greeting_night,
        )
        expected.forEach { (hour, title) -> assertEquals("hour $hour", title, homeGreetingForHour(hour).title) }
    }
}
