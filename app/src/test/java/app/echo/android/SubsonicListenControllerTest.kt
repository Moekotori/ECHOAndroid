package app.echo.android

import app.echo.android.data.SubsonicEndpoint
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoTrackRef
import app.echo.android.model.playback.PlaybackPositionState
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicListenControllerTest {
    @Test
    fun scrobbleSendsListenTimeInMilliseconds() {
        val submitted = ArrayList<SubmittedListen>()
        val now = AtomicLong(1_700_000_000_000L)
        val controller = controller(submitted, now)

        controller.onPlayback(playing(durationMs = 200_000L), position(0L, 200_000L))
        now.set(1_700_000_000_000L + 100_000L)
        controller.onPlayback(playing(durationMs = 200_000L), position(100_000L, 200_000L))

        val scrobble = submitted.single { it.submission }
        assertEquals(1_700_000_000_000L, scrobble.timeEpochMs)
        assertEquals("s1", scrobble.songId)
    }

    @Test
    fun failedScrobbleRetriesAfterBackoff() {
        val submitted = ArrayList<SubmittedListen>()
        var failNext = true
        val now = AtomicLong(1_000L)
        val controller = controller(submitted, now) { _, _, submission, _ ->
            if (submission && failNext) {
                failNext = false
                error("network")
            }
            submitted += SubmittedListen("s1", submission, if (submission) now.get() else null)
        }

        controller.onPlayback(playing(durationMs = 200_000L), position(0L, 200_000L))
        now.set(101_000L)
        controller.onPlayback(playing(durationMs = 200_000L), position(100_000L, 200_000L))
        assertEquals(0, submitted.count { it.submission })

        now.set(102_000L)
        controller.onPlayback(playing(durationMs = 200_000L), position(101_000L, 200_000L))
        assertEquals(0, submitted.count { it.submission })

        now.set(131_000L)
        controller.onPlayback(playing(durationMs = 200_000L), position(130_000L, 200_000L))
        assertEquals(1, submitted.count { it.submission })
    }

    @Test
    fun repeatOneStartsANewScrobble() {
        val submitted = ArrayList<SubmittedListen>()
        val now = AtomicLong(1_000L)
        val controller = controller(submitted, now)

        controller.onPlayback(playing(durationMs = 200_000L), position(0L, 200_000L))
        now.set(101_000L)
        controller.onPlayback(playing(durationMs = 200_000L), position(100_000L, 200_000L))
        assertEquals(1, submitted.count { it.submission })

        now.set(102_000L)
        controller.onPlayback(playing(durationMs = 200_000L), position(400L, 200_000L))
        now.set(202_000L)
        controller.onPlayback(playing(durationMs = 200_000L), position(100_400L, 200_000L))
        assertEquals(2, submitted.count { it.submission })
        assertTrue(submitted.filter { it.submission }.all { it.timeEpochMs != null && it.timeEpochMs >= 1_000L })
    }

    private fun controller(
        submitted: MutableList<SubmittedListen>,
        now: AtomicLong,
        submit: (
            SubsonicEndpoint,
            String,
            Boolean,
            Long?,
        ) -> Unit = { _, songId, submission, timeEpochMs ->
            submitted += SubmittedListen(songId, submission, timeEpochMs)
        },
    ): SubsonicListenController {
        val endpoint = SubsonicEndpoint(
            baseUrl = "https://navidrome.example",
            username = "user",
            password = "pass",
        )
        return SubsonicListenController(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            endpointRef = AtomicReference(endpoint),
            nowEpochMs = { now.get() },
            ioDispatcher = Dispatchers.Unconfined,
            submitListen = submit,
        )
    }

    private fun playing(durationMs: Long): EchoPlaybackStatus =
        EchoPlaybackStatus(
            state = EchoPlaybackState.Playing,
            track = EchoTrackRef(
                id = "subsonic:demo:song:s1",
                uri = "https://navidrome.example/rest/stream.view?id=s1",
                title = "Song",
                artist = "Artist",
                durationMs = durationMs,
            ),
            isPlaying = true,
            durationMs = durationMs,
        )

    private fun position(positionMs: Long, durationMs: Long) =
        PlaybackPositionState(positionMs = positionMs, durationMs = durationMs)
}

private data class SubmittedListen(
    val songId: String,
    val submission: Boolean,
    val timeEpochMs: Long?,
)
