package app.echo.android.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrooveSpiralMathTest {
    private val track = GrooveLyricsTrack(
        starts = longArrayOf(4_000, 8_000, 12_000),
        ends = longArrayOf(8_000, 12_000, 16_000),
        lengths = floatArrayOf(100f, 140f, 80f),
        gap = 30f,
        introLead = 60f,
    )

    @Test fun grooveRunsInwardAndNeedleSitsOneTurnIn() {
        val spiral = GrooveSpiralGeometry()
        for (k in 1 until spiral.lengths.size) assertTrue(spiral.lengths[k] > spiral.lengths[k - 1])
        // Needle is directly above the centre.
        assertEquals(0f, spiral.xs[spiral.needleIndex], 0.01f)
        assertTrue(spiral.ys[spiral.needleIndex] < 0f)
        assertTrue(spiral.needleLength in 1f..spiral.totalLength)
        // Radius shrinks from rim to label.
        val first = kotlin.math.hypot(spiral.xs.first(), spiral.ys.first())
        val last = kotlin.math.hypot(spiral.xs.last(), spiral.ys.last())
        assertEquals(0.926f, first, 0.001f)
        assertEquals(0.337f, last, 0.01f)
    }

    @Test fun cursorNeverJumpsAtALineBoundary() {
        assertEquals(track.cursor(7_999), track.cursor(8_000), 0.5f)
        assertEquals(track.cursor(11_999), track.cursor(12_000), 0.5f)
        assertEquals(0f, track.cursor(4_000), 0.001f)
    }

    @Test fun cursorOnlyMovesForward() {
        var previous = track.cursor(0)
        var at = 0L
        while (at <= 20_000) {
            val now = track.cursor(at)
            assertTrue("cursor went back at $at", now >= previous - 0.0001f)
            previous = now
            at += 50
        }
    }

    @Test fun introLeadsInAndLinesAreFoundByStartTime() {
        assertEquals(-60f, track.cursor(0), 0.001f)
        assertEquals(-1, track.indexAt(3_999))
        assertEquals(0, track.indexAt(4_000))
        assertEquals(2, track.indexAt(15_000))
        assertEquals(0.5f, track.progress(0, 4_000 + (4_000 * 0.88f / 2).toLong()), 0.01f)
    }
}
