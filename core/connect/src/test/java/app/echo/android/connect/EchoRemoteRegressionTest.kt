package app.echo.android.connect

import app.echo.android.model.connect.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class EchoRemoteRegressionTest {
    private val endpoint = EchoRemoteEndpoint("pc", "PC", "127.0.0.1", 26789, "paired-token")
    private fun playback() = EchoRemotePlaybackSnapshot(
        state = EchoRemotePlaybackState.Playing,
        track = EchoRemoteTrack("song", "Song", "Artist", null, null, 60_000),
        durationMs = 60_000,
    )
    private suspend fun eventually(predicate: () -> Boolean) {
        withTimeout(2_000) { while (!predicate()) delay(5) }
    }

    @Test fun queuedCommandsCannotEscapeAfterDisconnectAndReconnectToTheSamePc() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val commands = mutableListOf<EchoRemoteCommand>()
        val transport = object : EchoLinkTransport by FakeEchoLinkTransport() {
            override suspend fun sendCommand(endpoint: EchoRemoteEndpoint, command: EchoRemoteCommand): EchoLinkStatusResponse {
                commands += command
                entered.complete(Unit)
                release.await()
                return EchoLinkStatusResponse("PC", playback())
            }
        }
        val client = EchoRemoteClient(this, transport)
        var acknowledged = false
        try {
            client.connect(endpoint, false)
            eventually { client.status.value.connectionState == EchoRemoteConnectionState.Connected }
            client.send(EchoRemoteCommand.Next)
            entered.await()
            client.send(EchoRemoteCommand.Previous) { acknowledged = true }
            yield() // Previous is now waiting for the command mutex.
            client.disconnect()
            client.connect(endpoint, false)
            eventually { client.status.value.connectionState == EchoRemoteConnectionState.Connected }
            release.complete(Unit)
            delay(25)
            assertEquals(listOf(EchoRemoteCommand.Next), commands)
            assertFalse(acknowledged)
        } finally { release.complete(Unit); client.disconnect() }
    }

    @Test fun commandsWaitingForTheirCoroutineCannotBelongToANewConnection() = runBlocking {
        val transport = FakeEchoLinkTransport()
        val client = EchoRemoteClient(this, transport)
        try {
            client.connect(endpoint, false)
            eventually { client.status.value.connectionState == EchoRemoteConnectionState.Connected }
            client.send(EchoRemoteCommand.Next)
            client.disconnect()
            client.connect(endpoint, false)
            eventually { client.status.value.connectionState == EchoRemoteConnectionState.Connected }
            assertTrue(transport.commands.isEmpty())
        } finally { client.disconnect() }
    }

    @Test fun legacyQueueIdentityDoesNotCancelASeekBoundToTheV2Queue() = runBlocking {
        var actual = playback().copy(queue = EchoRemotePlaybackQueue(currentTrackId = "song", currentQueueId = "echo-link-1-song"))
        val commands = mutableListOf<EchoRemoteCommand>()
        val transport = object : EchoLinkTransport by FakeEchoLinkTransport() {
            override suspend fun fetchStatus(endpoint: EchoRemoteEndpoint) = EchoLinkStatusResponse("PC", actual)
            override suspend fun sendCommand(endpoint: EchoRemoteEndpoint, command: EchoRemoteCommand): EchoLinkStatusResponse {
                commands += command
                actual = actual.copy(positionMs = (command as EchoRemoteCommand.SeekTo).positionMs)
                return EchoLinkStatusResponse("PC", actual)
            }
        }
        val client = EchoRemoteClient(this, transport)
        try {
            client.connect(endpoint.copy(supportsV2Events = true), false)
            eventually { client.status.value.connectionState == EchoRemoteConnectionState.Connected }
            client.ingest(EchoRemoteMessage.StatusSnapshot(actual.copy(
                queue = EchoRemotePlaybackQueue(currentTrackId = "song", revision = 7, currentQueueId = "native-occurrence"),
                queueIdentityAvailable = true,
            )))
            var confirmed = false
            client.send(EchoRemoteCommand.SeekTo(10_000)) { confirmed = true }
            eventually { confirmed }
            assertEquals(listOf(EchoRemoteCommand.SeekTo(10_000)), commands)
            assertEquals("native-occurrence", client.status.value.playback.queue.currentQueueId)
            assertEquals(7L, client.status.value.playback.queue.revision)
        } finally { client.disconnect() }
    }

    @Test fun rejectedAbsoluteControlsKeepTheirErrorAcrossTheNextStatusSnapshot() = runBlocking {
        val transport = object : EchoLinkTransport by FakeEchoLinkTransport() {
            override suspend fun fetchStatus(endpoint: EchoRemoteEndpoint) = EchoLinkStatusResponse("PC", playback())
            override suspend fun sendCommand(endpoint: EchoRemoteEndpoint, command: EchoRemoteCommand): EchoLinkStatusResponse =
                throw EchoLinkHttpException("fixed_volume", 409)
        }
        val client = EchoRemoteClient(this, transport)
        try {
            client.connect(endpoint, false)
            eventually { client.status.value.connectionState == EchoRemoteConnectionState.Connected }
            client.send(EchoRemoteCommand.SetVolume(0.5f))
            eventually { client.status.value.error != null && !client.status.value.controlPending }
            val error = client.status.value.error
            client.ingest(EchoRemoteMessage.StatusSnapshot(playback().copy(positionMs = 500)))
            assertEquals(error, client.status.value.error)
            assertEquals(EchoRemoteConnectionState.Connected, client.status.value.connectionState)
        } finally { client.disconnect() }
    }
}
