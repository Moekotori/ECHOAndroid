package app.echo.android.connect

import app.echo.android.model.connect.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class EchoLinkSessionCompatibilityTest {
    private val endpoint = EchoRemoteEndpoint("pc", "PC", "127.0.0.1", 26789, "paired-token")
    private suspend fun eventually(predicate: () -> Boolean) {
        withTimeout(2_000) { while (!predicate()) delay(5) }
    }

    @Test fun controlsUseTheExchangedTokenImmediatelyAfterPairing() = runBlocking {
        val credentials = mutableListOf<String>()
        var playback = EchoRemotePlaybackSnapshot(
            state = EchoRemotePlaybackState.Playing,
            track = EchoRemoteTrack("song", "Song", "Artist", null, null, 60_000),
            durationMs = 60_000,
        )
        val transport = object : EchoLinkTransport by FakeEchoLinkTransport() {
            override suspend fun fetchStatus(endpoint: EchoRemoteEndpoint): EchoLinkStatusResponse {
                assertEquals("access-token", endpoint.token)
                return EchoLinkStatusResponse("PC", playback)
            }
            override suspend fun sendCommand(endpoint: EchoRemoteEndpoint, command: EchoRemoteCommand): EchoLinkStatusResponse {
                credentials += endpoint.token
                playback = playback.copy(positionMs = (command as EchoRemoteCommand.SeekTo).positionMs)
                return EchoLinkStatusResponse("PC", playback)
            }
        }
        val client = EchoRemoteClient(this, transport)
        try {
            client.connect(endpoint.copy(token = "pairing-secret", pairingId = "pair", pairingSecret = "pairing-secret"), false)
            eventually { client.status.value.connectionState == EchoRemoteConnectionState.Connected }
            var confirmed = false
            client.send(EchoRemoteCommand.SeekTo(5_000)) { confirmed = true }
            eventually { confirmed }
            assertEquals(listOf("access-token"), credentials)
            assertEquals(5_000L, client.status.value.playback.positionMs)
        } finally { client.disconnect() }
    }

    @Test fun aSyncReadCannotFinishAgainstAReconnectedSession() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val transport = object : EchoLinkTransport by FakeEchoLinkTransport() {
            override suspend fun syncCollections(endpoint: EchoRemoteEndpoint): List<EchoSyncCollection> {
                entered.complete(Unit); release.await()
                return listOf(EchoSyncCollection("favorites", "", 1, true))
            }
        }
        val client = EchoRemoteClient(this, transport)
        try {
            client.connect(endpoint, false)
            eventually { client.status.value.connectionState == EchoRemoteConnectionState.Connected }
            val session = client.librarySyncSession()
            val result = async { runCatching { session.collections() } }
            entered.await()
            client.disconnect()
            client.connect(endpoint, false)
            eventually { client.status.value.connectionState == EchoRemoteConnectionState.Connected }
            release.complete(Unit)
            assertTrue(result.await().exceptionOrNull() is IllegalStateException)
            assertTrue(runCatching { session.collections() }.exceptionOrNull() is IllegalStateException)
        } finally { release.complete(Unit); client.disconnect() }
    }

    @Test fun officialV2QueueSummaryKeepsRowsAndUsesThePcClock() {
        val row = EchoRemoteTrack("song", "Song", "Artist", null, null, 60_000)
        val previous = EchoRemotePlaybackSnapshot(queue = EchoRemotePlaybackQueue("song", listOf(row)))
        val event = parseEchoLinkEventData("""{"snapshot":{
            "state":"playing","observedAt":"2026-10-31T10:00:00Z",
            "track":{"id":"song","title":"Song","artist":"Artist"},
            "queue":{"revision":9,"currentQueueId":"item-2","currentTrackId":"song","total":4}
        }}""", endpoint)!!
        val merged = mergeEchoLinkSnapshot(previous, event)
        assertSame(previous.queue.items, merged.queue.items)
        assertEquals(9L, merged.queue.revision)
        assertEquals("item-2", merged.queue.currentQueueId)
        assertEquals(java.time.Instant.parse("2026-10-31T10:00:00Z").toEpochMilli(), merged.updatedAtEpochMs)
    }
}
