package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLEncoder

class EchoPairingParserTest {
    @Test
    fun parseV1PairingUri() {
        val endpoint = EchoPairingParser.parse(
            "echo://pair?host=192.168.1.20&port=26789&token=abcdefghijklmnop&name=PC%20ECHO&scheme=http",
        )
        assertNotNull(endpoint)
        assertEquals("192.168.1.20", endpoint!!.host)
        assertEquals(26789, endpoint.port)
        assertEquals("abcdefghijklmnop", endpoint.token)
        assertFalse(endpoint.needsV2PairExchange)
    }

    @Test
    fun parseV2PairingUri() {
        val endpoint = EchoPairingParser.parse(
            "echo://pair?version=2&scheme=http&host=192.168.1.20&port=26789&pairingId=pair-1&secret=super-secret-token-value&name=PC%20ECHO",
        )
        assertNotNull(endpoint)
        assertEquals("192.168.1.20", endpoint!!.host)
        assertEquals("pair-1", endpoint.pairingId)
        assertEquals("super-secret-token-value", endpoint.pairingSecret)
        assertTrue(endpoint.needsV2PairExchange)
    }

    @Test
    fun parseBasicRemoteUrlWithPairFragment() {
        val pairingUri =
            "echo://pair?version=2&host=192.168.1.20&port=26789&pairingId=pair-1&secret=super-secret-token-value"
        val remoteUrl = "http://192.168.1.20:26789/echo-link/v2/remote#pair=${URLEncoder.encode(pairingUri, "UTF-8")}"
        val endpoint = EchoPairingParser.parse(remoteUrl)
        assertNotNull(endpoint)
        assertEquals("pair-1", endpoint!!.pairingId)
        assertEquals("super-secret-token-value", endpoint.pairingSecret)
        assertTrue(endpoint.needsV2PairExchange)
    }

    @Test
    fun rejectNonEchoQr() {
        assertNull(EchoPairingParser.parse("https://example.test/not-echo"))
        assertNull(EchoPairingParser.parse("plain text"))
        assertNull(EchoPairingParser.parse("echo://pair?host=192.168.1.20"))
    }

    @Test
    fun handoffCommandTargetsPcWithPosition() {
        val json = EchoRemoteCommand.HandoffToPc("track-1", 42_000).toJson()
        assertEquals("handoff", json.getString("command"))
        assertEquals("track-1", json.getString("trackId"))
        assertEquals(42_000, json.getLong("positionMs"))
        assertEquals("pc", json.getString("target"))
    }

    @Test
    fun playOnPcCommandUsesOutputPc() {
        val json = EchoRemoteCommand.PlayTrackOnPc("track-2").toJson()
        assertEquals("playTrack", json.getString("command"))
        assertEquals("pc", json.getString("output"))
    }

    @Test
    fun playlistTracksUrlDoesNotFallBackToLibraryTracks() {
        val endpoint = EchoPairingParser.parse(
            "echo://pair?host=192.168.1.20&port=26789&token=abcdefghijklmnop",
        )!!
        val url = echoLinkPlaylistTracksUrl(endpoint, "playlist-1", 500)
        assertTrue(url.encodedPath.contains("/library/playlists/playlist-1/tracks"))
        assertFalse(url.queryParameterNames.contains("playlistId"))
        assertFalse(url.encodedPath.endsWith("/library/tracks"))
    }
}
