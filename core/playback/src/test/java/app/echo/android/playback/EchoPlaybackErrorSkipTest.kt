package app.echo.android.playback

import app.echo.android.model.playback.EchoAudioErrorKind
import app.echo.android.model.playback.EchoPlaybackError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoPlaybackErrorSkipTest {
    @Test
    fun unrecoverableErrorAutoSkips() {
        val error = EchoPlaybackError(
            kind = EchoAudioErrorKind.FileMissing,
            message = "missing",
            recoverable = false,
        )
        assertTrue(error.shouldAutoSkipTrack())
        assertEquals(
            2,
            nextIndexAfterPlaybackError(
                currentIndex = 1,
                mediaItemCount = 4,
                repeatAll = false,
                consecutiveErrorSkips = 0,
            ),
        )
    }

    @Test
    fun recoverableErrorDoesNotAutoSkip() {
        val error = EchoPlaybackError(
            kind = EchoAudioErrorKind.NetworkFailure,
            message = "timeout",
            recoverable = true,
        )
        assertFalse(error.shouldAutoSkipTrack())
    }

    @Test
    fun wrapsToStartWhenRepeatAll() {
        assertEquals(
            0,
            nextIndexAfterPlaybackError(
                currentIndex = 2,
                mediaItemCount = 3,
                repeatAll = true,
                consecutiveErrorSkips = 0,
            ),
        )
    }

    @Test
    fun consecutiveSkipsAccumulateAcrossDifferentMediaIds() {
        assertEquals(
            1,
            nextIndexAfterPlaybackError(
                currentIndex = 0,
                mediaItemCount = 3,
                repeatAll = true,
                consecutiveErrorSkips = 1,
            ),
        )
        assertNull(
            nextIndexAfterPlaybackError(
                currentIndex = 1,
                mediaItemCount = 3,
                repeatAll = true,
                consecutiveErrorSkips = 3,
            ),
        )
    }

    @Test
    fun stopsAfterVisitingEveryItem() {
        assertNull(
            nextIndexAfterPlaybackError(
                currentIndex = 0,
                mediaItemCount = 3,
                repeatAll = true,
                consecutiveErrorSkips = 3,
            ),
        )
    }

    @Test
    fun decodeFailureAutoSkipsLikeMissingFile() {
        val decode = EchoPlaybackError(
            kind = EchoAudioErrorKind.DecodeFailure,
            message = "decode",
            recoverable = true,
        )
        val missing = EchoPlaybackError(
            kind = EchoAudioErrorKind.FileMissing,
            message = "missing",
            recoverable = false,
        )
        val network = EchoPlaybackError(
            kind = EchoAudioErrorKind.NetworkFailure,
            message = "network",
            recoverable = true,
        )

        assertTrue(decode.shouldAutoSkipTrack())
        assertTrue(missing.shouldAutoSkipTrack())
        assertFalse(network.shouldAutoSkipTrack())
        assertNull(
            nextIndexAfterPlaybackError(
                currentIndex = 0,
                mediaItemCount = 1,
                repeatAll = true,
                consecutiveErrorSkips = 0,
            ),
        )
    }
}
