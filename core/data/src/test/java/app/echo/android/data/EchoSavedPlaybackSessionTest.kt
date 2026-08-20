package app.echo.android.data

import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.EchoTrackRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoSavedPlaybackSessionTest {
    @Test
    fun shuffleRepeatAndSpeedRoundTripWithQueueAndPosition() {
        val session = EchoSavedPlaybackSession(
            queue = listOf(
                EchoTrackRef(
                    id = "track-1",
                    uri = "content://echo/track-1",
                    title = "One",
                    artist = "Artist",
                    album = "Album",
                    artworkUri = null,
                    durationMs = 180_000L,
                ),
                EchoTrackRef(
                    id = "track-2",
                    uri = "content://echo/track-2",
                    title = "Two",
                    artist = "Artist",
                    durationMs = 200_000L,
                ),
            ),
            currentIndex = 1,
            positionMs = 45_000L,
            playWhenReady = true,
            shuffleEnabled = true,
            repeatMode = EchoRepeatMode.All,
            playbackSpeed = 1.25f,
            playbackPitch = 1.25f,
        )

        val parsed = parsePlaybackSession(session.toPreferenceValue())
        assertNotNull(parsed)
        checkNotNull(parsed)
        assertEquals(1, parsed.currentIndex)
        assertEquals(45_000L, parsed.positionMs)
        assertTrue(parsed.playWhenReady)
        assertTrue(parsed.shuffleEnabled)
        assertEquals(EchoRepeatMode.All, parsed.repeatMode)
        assertEquals(1.25f, parsed.playbackSpeed, 0.001f)
        assertEquals(1.25f, parsed.playbackPitch, 0.001f)
        assertEquals(listOf("track-1", "track-2"), parsed.queue.map { it.id })
    }
}
