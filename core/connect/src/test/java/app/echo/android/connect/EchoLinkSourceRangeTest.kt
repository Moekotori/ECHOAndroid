package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoRemoteTrack
import app.echo.android.model.connect.EchoRemoteSourceRange
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EchoLinkSourceRangeTest {
    @Test fun parsesSourceBoundsAndBuildsClippedPlaybackWithoutMovingLogicalTime() {
        val endpoint = EchoRemoteEndpoint("pc", "PC", "192.168.1.2", 26789, "test-only")
        val response = JSONObject("""{"streamUrl":"http://192.168.1.2/echo-link/media/test","sourceRange":{"startSeconds":60.5,"endSeconds":120.5},"track":{"id":"cue","title":"Song","artist":"Artist"}}""")
            .toStreamResponse(endpoint)
        val track = requireNotNull(response.track).toPhonePlaybackTrack(response.streamUrl, response.sourceRange)
        assertEquals(60500L, track.clipStartMs)
        assertEquals(120500L, track.clipEndMs)
        assertEquals(0L, track.durationMs)
        assertEquals(response.sourceRange, JSONObject().put("sourceRange", response.sourceRange!!.toJson()).sourceRange())
    }

    @Test fun previewBoundsSurviveUntilAnUnresolvedQueueItemIsOpened() {
        val item = EchoRemoteTrack("cue", "Song", "Artist", null, null, 60000,
            sourceRange = EchoRemoteSourceRange(60.0, 120.0))
            .toPhonePlaybackTrack("echo-link://track/cue")
        assertEquals(60000L, item.clipStartMs)
        assertEquals(120000L, item.clipEndMs)
        assertEquals(60000L, item.durationMs)
    }

    @Test fun onlyEchoLinkOptsIntoSegmentedCasting() {
        val track = EchoLinkCastSourceTrack("cue", "content://media/1", "Song", "Artist", clipStartMs = 60000, clipEndMs = 120000)
        assertTrue(EchoLinkCastPolicy.plan(listOf(track), 0) is EchoLinkCastPlan.Blocked)
        val plan = EchoLinkCastPolicy.plan(listOf(track), 0, sourceRanges = true) as EchoLinkCastPlan.LocalHttp
        assertEquals(track, plan.tracks.single())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsReversedBoundaries() { EchoRemoteSourceRange(120.0, 60.0) }
}
