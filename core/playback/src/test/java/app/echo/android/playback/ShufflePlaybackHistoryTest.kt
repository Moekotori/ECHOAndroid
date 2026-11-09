package app.echo.android.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class ShufflePlaybackHistoryTest {
    @Test fun recordsVisitsAndRewindsWithoutRecordingTheDepartingTrackAgain() {
        val history = ShufflePlaybackHistory()
        history.observe("a", manual = false, playing = true)
        history.observe("manual", manual = true, playing = true)
        history.observe("skipped", manual = false, playing = false)
        history.observe("b", manual = false, playing = true)
        history.observe("c", manual = false, playing = true)
        assertEquals(listOf("a", "b"), history.entries())
        history.rewindTo("b", manual = false)
        history.observe("b", manual = false, playing = true)
        assertEquals(listOf("a"), history.entries())
    }
}
