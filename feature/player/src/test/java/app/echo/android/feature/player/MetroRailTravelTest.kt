package app.echo.android.feature.player

import app.echo.android.model.lyrics.EchoLyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MetroRailTravelTest {
    private val lines = listOf(
        EchoLyricLine(1000, null, "A"),
        EchoLyricLine(5000, null, "B"),
        EchoLyricLine(5000, null, "same start"),
        EchoLyricLine(9000, null, "C"),
    )

    @Test fun onlyTheCurrentAndNextRowsTravel() {
        assertNotNull(metroRailTravel(lines, 0, 0))
        assertNotNull(metroRailTravel(lines, 1, 0))
        assertNull(metroRailTravel(lines, 2, 0))
        assertNull(metroRailTravel(lines, 0, -1))
    }

    @Test fun noTripWithoutANextStationOrADistinctStart() {
        assertNull(metroRailTravel(lines, 3, 3))
        assertNull(metroRailTravel(lines, 1, 1))
    }

    @Test fun progressRunsFromDepartureToArrivalAndStaysInRange() {
        val travel = MetroRailTravel(1000, 5000)
        assertEquals(0f, travel.progressAt(0), 0f)
        assertEquals(0.5f, travel.progressAt(3000), 0.0001f)
        assertEquals(1f, travel.progressAt(9000), 0f)
    }

    @Test fun theTwoRowsShareOneTripWithoutAJump() {
        assertEquals(0f, metroArriveFill(0.5f), 0f)
        assertEquals(1f, metroDepartFill(0.5f), 0f)
        assertEquals(0.5f, metroDepartFill(0.25f), 0f)
        assertEquals(0.5f, metroArriveFill(0.75f), 0f)
    }
}
