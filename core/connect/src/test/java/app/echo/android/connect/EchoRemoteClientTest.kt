package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteCommand
import app.echo.android.model.connect.EchoRemoteConnectionState
import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoRemoteLyrics
import app.echo.android.model.connect.EchoRemotePlaybackSnapshot
import app.echo.android.model.connect.EchoRemotePlaylist
import app.echo.android.model.connect.EchoRemoteTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoRemoteClientTest {
    private val endpoint = EchoRemoteEndpoint(
        id = "192.168.1.20:26789",
        name = "PC ECHO",
        host = "192.168.1.20",
        port = 26789,
        token = "abcdefghijklmnop",
    )

    @Test
    fun firstFailedConnectIsRetriedUntilStatusSucceeds() = runBlocking {
        val transport = FakeEchoLinkTransport(failStatusTimes = 1)
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(50)
        assertEquals(EchoRemoteConnectionState.Connected, client.status.value.connectionState)
        assertTrue(transport.statusCalls >= 2)
        client.disconnect()
    }

    @Test
    fun playlistTracks404DoesNotLoadTheGeneralLibrary() = runBlocking {
        val transport = FakeEchoLinkTransport()
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(50)
        client.refreshPlaylistTracks(
            EchoRemotePlaylist(id = "missing", name = "Missing", artworkUrl = null, trackCount = 0),
        )
        delay(50)
        assertEquals(1, transport.playlistTrackCalls)
        assertEquals(0, transport.libraryTrackCalls)
        assertTrue(client.library.value.error?.contains("404") == true)
        assertTrue(client.library.value.playlistTracks["missing"].isNullOrEmpty())
        client.disconnect()
    }

    @Test
    fun pagesBeyondTheFirstLibraryPage() = runBlocking {
        val transport = FakeEchoLinkTransport(libraryPageSize = 2, libraryTotalCount = 5)
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = true)
        delay(80)
        assertEquals(5, client.library.value.tracks.size)
        assertEquals(5, client.library.value.totalCount)
        assertEquals(3, transport.libraryTrackCalls)
        client.disconnect()
    }

    @Test
    fun canSendPlayOnPcAndHandoffCommands() = runBlocking {
        val transport = FakeEchoLinkTransport()
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(50)
        val track = EchoRemoteTrack(
            id = "track-1",
            title = "Song",
            artist = "Artist",
            album = "Album",
            artworkUrl = null,
            durationMs = 240_000,
            canPlayOnPhone = true,
        )
        client.playTrackOnPc(track)
        client.handoffToPc(track, 12_000)
        delay(50)
        assertTrue(transport.commands.any { it is EchoRemoteCommand.PlayTrackOnPc && it.trackId == "track-1" })
        assertTrue(transport.commands.any { it is EchoRemoteCommand.HandoffToPc && it.positionMs == 12_000L })
        client.disconnect()
    }

    @Test
    fun refusesUnstreamablePhonePlayback() = runBlocking {
        val transport = FakeEchoLinkTransport()
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0)
        client.connect(endpoint, refreshLibraryOnConnect = false)
        delay(50)
        client.playTrackOnPhone(
            track = EchoRemoteTrack(
                id = "track-dsd",
                title = "DSD",
                artist = "Artist",
                album = null,
                artworkUrl = null,
                durationMs = 240_000,
                canPlayOnPhone = false,
            ),
            onTrackReady = { error("should not start phone playback") },
        )
        delay(20)
        assertEquals(0, transport.streamCalls)
        assertTrue(client.library.value.error?.contains("串流") == true)
        client.disconnect()
    }
}

private class FakeEchoLinkTransport(
    private val failStatusTimes: Int = 0,
    private val libraryPageSize: Int = 500,
    private val libraryTotalCount: Int = 0,
) : EchoLinkTransport {
    var statusCalls = 0
    var libraryTrackCalls = 0
    var playlistTrackCalls = 0
    var streamCalls = 0
    val commands = mutableListOf<EchoRemoteCommand>()

    override suspend fun completePairing(endpoint: EchoRemoteEndpoint): EchoRemoteEndpoint = endpoint.copy(
        token = "access-token",
        pairingId = null,
        pairingSecret = null,
    )

    override suspend fun fetchStatus(endpoint: EchoRemoteEndpoint): EchoLinkStatusResponse {
        statusCalls += 1
        if (statusCalls <= failStatusTimes) {
            throw EchoLinkHttpException("PC ECHO request failed (503): starting")
        }
        return EchoLinkStatusResponse(
            deviceName = endpoint.name,
            playback = EchoRemotePlaybackSnapshot(),
        )
    }

    override suspend fun sendCommand(
        endpoint: EchoRemoteEndpoint,
        command: EchoRemoteCommand,
    ): EchoLinkStatusResponse? {
        commands += command
        return EchoLinkStatusResponse(deviceName = endpoint.name, playback = EchoRemotePlaybackSnapshot())
    }

    override suspend fun fetchTracks(
        endpoint: EchoRemoteEndpoint,
        query: String,
        page: Int,
        pageSize: Int,
    ): EchoLinkTrackPage {
        libraryTrackCalls += 1
        val start = (page - 1) * libraryPageSize
        if (start >= libraryTotalCount) {
            return EchoLinkTrackPage(tracks = emptyList(), totalCount = libraryTotalCount)
        }
        val end = minOf(libraryTotalCount, start + libraryPageSize)
        val tracks = (start until end).map { index ->
            EchoRemoteTrack(
                id = "track-$index",
                title = "Song $index",
                artist = "Artist",
                album = "Album",
                artworkUrl = null,
                durationMs = 1_000,
            )
        }
        return EchoLinkTrackPage(tracks = tracks, totalCount = libraryTotalCount)
    }

    override suspend fun fetchPlaylists(
        endpoint: EchoRemoteEndpoint,
        query: String,
        pageSize: Int,
    ): EchoLinkPlaylistPage = EchoLinkPlaylistPage(playlists = emptyList(), totalCount = 0)

    override suspend fun fetchPlaylistTracks(
        endpoint: EchoRemoteEndpoint,
        playlistId: String,
        pageSize: Int,
    ): EchoLinkTrackPage {
        playlistTrackCalls += 1
        throw EchoLinkHttpException("PC ECHO request failed (404): playlist_not_found")
    }

    override suspend fun resolveStream(endpoint: EchoRemoteEndpoint, trackId: String): EchoLinkStreamResponse {
        streamCalls += 1
        return EchoLinkStreamResponse(streamUrl = "http://192.168.1.20:26789/echo-link/media/token", track = null)
    }

    override suspend fun fetchLyrics(endpoint: EchoRemoteEndpoint, trackId: String): EchoRemoteLyrics? = null
}
