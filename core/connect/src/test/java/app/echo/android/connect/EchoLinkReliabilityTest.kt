package app.echo.android.connect

import app.echo.android.model.connect.*
import app.echo.android.model.playback.EchoLinkPlaybackUri
import app.echo.android.model.playback.EchoTrackRef
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class EchoLinkReliabilityTest {
    private val endpoint = EchoRemoteEndpoint("pc", "PC", "127.0.0.1", 26789, "paired-token")
    private val track = EchoRemoteTrack("song", "Song", "Artist", null, null, 60_000)

    private suspend fun eventually(predicate: () -> Boolean) {
        withTimeout(2_000) { while (!predicate()) delay(5) }
    }

    @Test fun recoveredInitialConnectionLoadsTheRequestedLibrary() = runBlocking {
        val transport = FakeEchoLinkTransport(failStatusTimes = 2, libraryTotalCount = 1)
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0, statusPollIntervalMs = 10)
        try {
            client.connect(endpoint)
            eventually { client.status.value.connectionState == EchoRemoteConnectionState.Connected }
            eventually { client.library.value.tracks.size == 1 }
            assertEquals(1, transport.libraryTrackCalls)
        } finally { client.disconnect() }
    }

    @Test fun recoveredBackgroundConnectionDefersTheLibraryUntilForeground() = runBlocking {
        val transport = FakeEchoLinkTransport(failStatusTimes = 2, libraryTotalCount = 1)
        val client = EchoRemoteClient(this, transport, connectRetryDelayMs = 0, backgroundStatusPollIntervalMs = 10)
        try {
            client.setForeground(false)
            client.setBackgroundConnectionRequired(true)
            client.connect(endpoint)
            eventually { client.status.value.connectionState == EchoRemoteConnectionState.Connected }
            assertEquals(0, transport.libraryTrackCalls)
            client.setForeground(true)
            eventually { client.library.value.tracks.size == 1 }
        } finally { client.disconnect() }
    }

    @Test fun readsCannotReturnAcrossAnIdenticalReconnect() = runBlocking {
        for (operation in listOf("search", "search-stream", "playback-stream", "lyrics")) {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            suspend fun waitForReconnect() { entered.complete(Unit); release.await() }
            val transport = object : EchoLinkTransport by FakeEchoLinkTransport() {
                override suspend fun fetchTracks(endpoint: EchoRemoteEndpoint, query: String, page: Int, pageSize: Int): EchoLinkTrackPage {
                    waitForReconnect(); return EchoLinkTrackPage(listOf(track), 1)
                }
                override suspend fun resolveStream(endpoint: EchoRemoteEndpoint, trackId: String): EchoLinkStreamResponse {
                    waitForReconnect(); return EchoLinkStreamResponse("http://127.0.0.1/old-stream", null)
                }
                override suspend fun fetchLyrics(endpoint: EchoRemoteEndpoint, trackId: String): EchoRemoteLyrics {
                    waitForReconnect(); return EchoRemoteLyrics("old lyrics", "PC")
                }
            }
            val client = EchoRemoteClient(this, transport)
            try {
                client.connect(endpoint, false)
                eventually { client.status.value.connectionState == EchoRemoteConnectionState.Connected }
                val result = async {
                    runCatching {
                        when (operation) {
                            "search" -> client.searchTracksPage("song", 1, 30)
                            "search-stream" -> client.resolveSearchTrackOnPhone(track)
                            "playback-stream" -> client.resolvePhonePlaybackTrack(EchoTrackRef(
                                id = EchoLinkPlaybackUri.mediaId("song"),
                                uri = EchoLinkPlaybackUri.persistUri("song"), title = "Song", artist = "Artist"))
                            else -> client.fetchLyrics("song")
                        }
                    }
                }
                entered.await()
                client.disconnect()
                client.connect(endpoint, false)
                eventually { client.status.value.connectionState == EchoRemoteConnectionState.Connected }
                release.complete(Unit)
                val completed = result.await()
                assertTrue("Stale $operation escaped its session", completed.isFailure || completed.getOrNull() == null)
            } finally { release.complete(Unit); client.disconnect() }
        }
    }

    @Test fun incompleteAlbumAndPlaylistPagesFailAndRemainRetryable() = runBlocking {
        for (album in listOf(false, true)) {
            var incomplete = true
            val pages = mutableListOf<Int>()
            fun batch(page: Int): EchoLinkTrackPage {
                pages += page
                return EchoLinkTrackPage(if (page == 1 || !incomplete) listOf(track.copy(id = "song-$page")) else emptyList(), 2)
            }
            val transport = object : EchoLinkTransport by FakeEchoLinkTransport() {
                override suspend fun fetchAlbumTracks(endpoint: EchoRemoteEndpoint, albumId: String, page: Int, pageSize: Int) = batch(page)
                override suspend fun fetchPlaylistTracks(endpoint: EchoRemoteEndpoint, playlistId: String, page: Int, pageSize: Int) = batch(page)
            }
            val client = EchoRemoteClient(this, transport)
            fun refresh() {
                if (album) client.refreshAlbumTracks(EchoRemoteAlbum("collection", "Album", "Artist", trackCount = 2))
                else client.refreshPlaylistTracks(EchoRemotePlaylist("collection", "Playlist", trackCount = 2))
            }
            try {
                client.connect(endpoint, false)
                eventually { client.status.value.connectionState == EchoRemoteConnectionState.Connected }
                refresh()
                eventually { client.library.value.loadingAlbumId == null && client.library.value.loadingPlaylistId == null }
                assertNotNull("Incomplete collection was reported as successful", client.library.value.error)
                assertTrue(client.library.value.albumTracks.isEmpty())
                assertTrue(client.library.value.playlistTracks.isEmpty())
                incomplete = false
                refresh()
                eventually { (if (album) client.library.value.albumTracks else client.library.value.playlistTracks)["collection"]?.size == 2 }
                assertEquals(listOf(1, 2, 1, 2), pages)
                assertNull(client.library.value.error)
            } finally { client.disconnect() }
        }
    }
}
