package app.echo.android.data

import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.EchoTrackRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoSavedPlaybackSessionTest {
    @Test
    fun shuffleRepeatAndSpeedRoundTripWithQueueAndPosition() {
        val session = savedSession(
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

    @Test
    fun matchingResumeUpdatesOnlyDynamicPlaybackState() {
        val stored = savedSession(currentIndex = 0, positionMs = 1_000L)
        val resumed = savedSession(
            currentIndex = 1,
            positionMs = 92_000L,
            playWhenReady = true,
            shuffleEnabled = true,
            repeatMode = EchoRepeatMode.One,
            playbackSpeed = 1.5f,
            playbackPitch = 0.9f,
        )
        val parsedResume = parsePlaybackResume(
            resumed.toResumePreferenceValue(stored.playbackQueueIdentity()),
        )

        val result = stored.withPlaybackResume(parsedResume)

        assertEquals(stored.queue, result.queue)
        assertEquals(1, result.currentIndex)
        assertEquals(92_000L, result.positionMs)
        assertTrue(result.playWhenReady)
        assertTrue(result.shuffleEnabled)
        assertEquals(EchoRepeatMode.One, result.repeatMode)
        assertEquals(1.5f, result.playbackSpeed, 0.001f)
        assertEquals(0.9f, result.playbackPitch, 0.001f)
    }

    @Test
    fun staleResumeFromDifferentQueueIsIgnored() {
        val stored = savedSession(currentIndex = 0, positionMs = 1_000L)
        val otherQueue = savedSession(currentIndex = 1, positionMs = 92_000L).copy(
            queue = listOf(stored.queue.first().copy(id = "replacement")),
            currentIndex = 0,
        )
        val staleResume = parsePlaybackResume(
            otherQueue.toResumePreferenceValue(otherQueue.playbackQueueIdentity()),
        )

        val result = stored.withPlaybackResume(staleResume)

        assertEquals(0, result.currentIndex)
        assertEquals(1_000L, result.positionMs)
        assertFalse(result.playWhenReady)
    }

    private fun savedSession(
        currentIndex: Int,
        positionMs: Long,
        playWhenReady: Boolean = false,
        shuffleEnabled: Boolean = false,
        repeatMode: EchoRepeatMode = EchoRepeatMode.Off,
        playbackSpeed: Float = 1f,
        playbackPitch: Float = 1f,
    ): EchoSavedPlaybackSession = EchoSavedPlaybackSession(
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
        currentIndex = currentIndex,
        positionMs = positionMs,
        playWhenReady = playWhenReady,
        shuffleEnabled = shuffleEnabled,
        repeatMode = repeatMode,
        playbackSpeed = playbackSpeed,
        playbackPitch = playbackPitch,
    )
}
