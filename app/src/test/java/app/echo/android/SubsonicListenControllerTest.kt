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
    fun anotherAccountCannotReceiveThePreviousAccountListen() {
        val first = SubsonicEndpoint("https://navidrome.example", "first", "pass")
        val endpoint = AtomicReference<SubsonicEndpoint?>(first)
        val submitted = ArrayList<SubmittedListen>()
        val controller = SubsonicListenController(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), endpoint,
            ioDispatcher = Dispatchers.Unconfined, submitListen = { _, song, submission, time ->
                submitted += SubmittedListen(song, submission, time)
            })
        val status = playing(200_000).let { it.copy(track = it.track!!.copy(id = "${first.sourceId}:song:s1")) }
        controller.onPlayback(status, position(0, 200_000))
        val count = submitted.size
        endpoint.set(SubsonicEndpoint(first.baseUrl, "second", "other"))
        controller.onPlayback(status, position(150_000, 200_000))
        assertEquals(count, submitted.size)
    }

    @Test
    fun archivedAccountReceivesItsOwnLyricsRoutingAndListenReports() {
        val alice = SubsonicEndpoint("https://navidrome.example", "alice", "one")
        val bob = alice.copy(username = "bob", password = "two")
        val endpoints = listOf(bob, alice)
        val aliceId = "${alice.sourceId}:song:shared-song"
        assertEquals(alice, subsonicEndpointForTrack(aliceId, endpoints))
        assertEquals(null, subsonicEndpointForTrack("subsonic:unknown:song:shared-song", endpoints))
        val reports = mutableListOf<Pair<String, Boolean>>()
        val now = AtomicLong(1_000)
        val controller = SubsonicListenController(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            AtomicReference(bob), nowEpochMs = now::get,
            endpointForTrack = { subsonicEndpointForTrack(it, endpoints) }, ioDispatcher = Dispatchers.Unconfined,
            submitListen = { endpoint, _, submission, _ -> reports += endpoint.username to submission })
        val status = playing(200_000).let { it.copy(track = it.track!!.copy(id = aliceId)) }
        controller.onPlayback(status, position(0, 200_000))
        now.set(101_000)
        controller.onPlayback(status, position(100_000, 200_000))
        assertEquals(listOf("alice" to false, "alice" to true), reports)
        val next = status.copy(track = status.track!!.copy(id = "${bob.sourceId}:song:shared-song"))
        controller.onPlayback(next, position(0, 200_000))
        assertEquals("bob" to false, reports.last())
    }

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
                id = "${SubsonicEndpoint("https://navidrome.example", "user", "pass").sourceId}:song:s1",
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
