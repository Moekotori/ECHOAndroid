package app.echo.android.connect

import app.echo.android.model.playback.EchoLinkPlaybackUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLinkCastPolicyTest {
    @Test
    fun unknownSchemeIsBlocked() {
        val plan = EchoLinkCastPolicy.plan(
            listOf(source(id = "x", uri = "rtsp://cam/stream", sourceId = "unknown")),
            startIndex = 0,
        )
        assertEquals(EchoLinkCastBlockReason.UnsupportedSource, (plan as EchoLinkCastPlan.Blocked).reason)
    }

    @Test
    fun emptyQueueIsBlocked() {
        val plan = EchoLinkCastPolicy.plan(emptyList(), 0)
        assertTrue(plan is EchoLinkCastPlan.Blocked)
        assertEquals(EchoLinkCastBlockReason.EmptyQueue, (plan as EchoLinkCastPlan.Blocked).reason)
    }

    @Test
    fun remoteHttpSourceIsServedByPhone() {
        val plan = EchoLinkCastPolicy.plan(
            listOf(
                source(
                    id = "sub-1",
                    uri = "https://nas.example/rest/stream.view?id=1",
                    sourceId = "subsonic",
                ),
            ),
            startIndex = 0,
        ) as EchoLinkCastPlan.LocalHttp
        assertEquals(listOf("sub-1"), plan.tracks.map { it.id })
    }

    @Test
    fun phoneServedRunIncludesLocalAndRemoteHttp() {
        val plan = EchoLinkCastPolicy.plan(
            listOf(
                source(id = "a", uri = "content://media/1"),
                source(id = "b", uri = "file:///sdcard/b.flac"),
                source(id = "c", uri = "https://nas.example/c", sourceId = "subsonic"),
            ),
            startIndex = 1,
        ) as EchoLinkCastPlan.LocalHttp
        assertEquals(listOf("b", "c"), plan.tracks.map { it.id })
        assertEquals(0, plan.startIndex)
    }

    @Test
    fun echoLinkQueueUsesPcTrackIds() {
        val plan = EchoLinkCastPolicy.plan(
            listOf(
                source(id = EchoLinkPlaybackUri.mediaId("pc-a"), uri = "echo-link://track/pc-a"),
                source(id = EchoLinkPlaybackUri.mediaId("pc-b"), uri = "http://pc/echo-link/media/token"),
            ),
            startIndex = 0,
        ) as EchoLinkCastPlan.HandoffPcLibrary
        assertEquals(listOf("pc-a", "pc-b"), plan.tracks.map { it.id })
    }

    @Test
    fun mixedQueueStopsAtSourceChange() {
        val plan = EchoLinkCastPolicy.plan(
            listOf(
                source(id = "local", uri = "content://media/1"),
                source(id = EchoLinkPlaybackUri.mediaId("pc-a"), uri = "echo-link://track/pc-a"),
            ),
            startIndex = 0,
        ) as EchoLinkCastPlan.LocalHttp
        assertEquals(listOf("local"), plan.tracks.map { it.id })
    }

    @Test
    fun pickLanPrefersPrivateIpv4() {
        assertEquals(
            "192.168.1.20",
            EchoLinkCastPolicy.pickLanIpv4(listOf("127.0.0.1", "192.168.1.20", "8.8.8.8")),
        )
        assertNull(EchoLinkCastPolicy.pickLanIpv4(listOf("127.0.0.1", "fe80::1")))
    }

    @Test
    fun rangeParsing() {
        assertEquals(10L to 19L, EchoLinkCastPolicy.parseRange("bytes=10-19", 100))
        assertEquals(10L to 99L, EchoLinkCastPolicy.parseRange("bytes=10-", 100))
        assertNull(EchoLinkCastPolicy.parseRange("bytes=100-", 100))
        assertNull(EchoLinkCastPolicy.parseRange("bytes=20-10", 100))
    }

    @Test
    fun tokenShape() {
        val token = EchoLinkCastPolicy.newToken()
        assertEquals(32, token.length)
        assertTrue(EchoLinkCastPolicy.isCastToken(token))
        assertFalse(EchoLinkCastPolicy.isCastToken("../etc/passwd"))
        assertFalse(EchoLinkCastPolicy.isCastToken(""))
    }

    @Test
    fun mimeGuessesAudioExtensions() {
        assertEquals("audio/flac", EchoLinkCastPolicy.mimeTypeForUri("file:///a/b.flac"))
        assertEquals("audio/mpeg", EchoLinkCastPolicy.mimeTypeForUri("content://media/1.mp3"))
        assertEquals("audio/ogg", EchoLinkCastPolicy.mimeTypeForUri("ignored", "audio/ogg"))
    }

    @Test
    fun peerMatchAcceptsMappedIpv6AndHostnames() {
        assertTrue(EchoLinkCastPolicy.peerMatches("192.168.1.12", "192.168.1.12"))
        assertTrue(EchoLinkCastPolicy.peerMatches("192.168.1.12", "::ffff:192.168.1.12"))
        assertTrue(EchoLinkCastPolicy.peerMatches("pc.local", "192.168.1.12"))
        assertFalse(EchoLinkCastPolicy.peerMatches("192.168.1.12", "10.0.0.8"))
    }

    @Test
    fun contentRangeTotalReadsSlashSuffix() {
        assertEquals(256L, EchoLinkCastPolicy.contentRangeTotal("bytes 10-19/256"))
        assertNull(EchoLinkCastPolicy.contentRangeTotal("bytes 10-19/*"))
        assertNull(EchoLinkCastPolicy.contentRangeTotal(null))
    }

    private fun source(
        id: String,
        uri: String,
        sourceId: String? = "mediastore",
    ) = EchoLinkCastSourceTrack(
        id = id,
        uri = uri,
        title = id,
        artist = "Artist",
        sourceId = sourceId,
    )
}
