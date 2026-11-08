package app.echo.android.model.library

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ArtistConcertPhaseTest {
    private val now = Instant.parse("2026-11-08T10:00:00Z")
    private val event = ArtistConcert("show", "Live", "2026-11-08", "18:00", null, null, "source", "", timezone = "Asia/Tokyo")

    @Test fun knownEndKeepsOngoingShowOutOfHistory() {
        assertEquals(ArtistConcertPhase.Ongoing, event.copy(details = ArtistConcertDetails(endsAt = "2026-11-08T20:00:00")).phase(now))
        assertEquals(ArtistConcertPhase.Past, event.copy(details = ArtistConcertDetails(endsAt = "2026-11-08T19:00:00")).phase(now))
        assertEquals(ArtistConcertPhase.Started, event.phase(now))
    }

    @Test fun dateOnlyEndRetainsItsWholeCalendarDayAndMissingTimeIsNotMidnight() {
        val multiDay = event.copy(date = "2026-11-07", time = null, details = ArtistConcertDetails(endsAt = "2026-11-08"))
        assertEquals(ArtistConcertPhase.Upcoming, multiDay.phase(now))
        assertEquals(ArtistConcertPhase.Past, multiDay.phase(Instant.parse("2026-11-08T15:00:00Z")))
        assertEquals(ArtistConcertPhase.Upcoming, event.copy(time = null).phase(now))
    }

    @Test fun venueTimezoneAndExplicitOffsetOverridePhoneCalendar() {
        val midnight = event.copy(date = "2026-11-09", time = "00:30", details = ArtistConcertDetails(endsAt = "2026-11-08T16:30:00Z"))
        assertEquals(ArtistConcertPhase.Ongoing, midnight.phase(Instant.parse("2026-11-08T16:00:00Z")))
        assertEquals(ArtistConcertPhase.Upcoming, event.copy(timezone = "invalid").phase(now))
    }
}
