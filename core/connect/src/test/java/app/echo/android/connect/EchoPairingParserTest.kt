package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteCommand
import app.echo.android.model.connect.EchoRemoteStreamItem
import app.echo.android.model.connect.EchoRemoteTrack
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
    fun queueReplaceCommandIncludesTrackIdsAndStart() {
        val json = EchoRemoteCommand.QueueReplace(listOf("a", "b"), "b").toJson()
        assertEquals("queueReplace", json.getString("command"))
        assertEquals("b", json.getString("startTrackId"))
        assertEquals("pc", json.getString("output"))
        val ids = json.getJSONArray("trackIds")
        assertEquals(2, ids.length())
        assertEquals("a", ids.getString(0))
        assertEquals("b", ids.getString(1))
    }

    @Test
    fun playRemoteStreamCommandIncludesUrlAndTrack() {
        val json = EchoRemoteCommand.PlayRemoteStream(
            streamUrl = "http://192.168.1.20:26800/echo-link/cast/abcd",
            positionMs = 12_000,
            track = EchoRemoteTrack(
                id = "phone-1",
                title = "Song",
                artist = "Artist",
                album = "Album",
                artworkUrl = null,
                durationMs = 240_000,
            ),
        ).toJson()
        assertEquals("playRemoteStream", json.getString("command"))
        assertEquals("pc", json.getString("target"))
        assertEquals(12_000, json.getLong("positionMs"))
        assertEquals("http://192.168.1.20:26800/echo-link/cast/abcd", json.getString("streamUrl"))
        assertEquals("phone-1", json.getJSONObject("track").getString("id"))
    }

    @Test
    fun queueReplaceRemoteCommandIncludesItems() {
        val json = EchoRemoteCommand.QueueReplaceRemote(
            items = listOf(
                EchoRemoteStreamItem(
                    id = "a",
                    streamUrl = "http://phone/echo-link/cast/a",
                    title = "A",
                    artist = "Artist",
                ),
                EchoRemoteStreamItem(
                    id = "b",
                    streamUrl = "http://phone/echo-link/cast/b",
                    title = "B",
                    artist = "Artist",
                ),
            ),
            startTrackId = "b",
        ).toJson()
        assertEquals("queueReplaceRemote", json.getString("command"))
        assertEquals("b", json.getString("startTrackId"))
        assertEquals(2, json.getJSONArray("items").length())
        assertEquals("http://phone/echo-link/cast/a", json.getJSONArray("items").getJSONObject(0).getString("streamUrl"))
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

    @Test
    fun albumAndFolderUrlsStayOnTheirEndpoints() {
        val endpoint = EchoPairingParser.parse(
            "echo://pair?host=192.168.1.20&port=26789&token=abcdefghijklmnop",
        )!!
        val albums = echoLinkLibraryAlbumsUrl(endpoint, "radiohead", 1, 25)
        assertTrue(albums.encodedPath.contains("/library/albums"))
        assertEquals("radiohead", albums.queryParameter("q"))
        val albumTracks = echoLinkAlbumTracksUrl(endpoint, "album-1", 1, 500)
        assertTrue(albumTracks.encodedPath.contains("/library/albums/album-1/tracks"))
        val folders = echoLinkFoldersUrl(endpoint, "Music/Jazz")
        assertTrue(folders.encodedPath.contains("/library/folders"))
        assertEquals("Music/Jazz", folders.queryParameter("path"))
    }
}
