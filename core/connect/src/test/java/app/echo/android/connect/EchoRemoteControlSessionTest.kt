package app.echo.android.connect

import app.echo.android.model.connect.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class EchoRemoteControlSessionTest {
    private val endpoint = EchoRemoteEndpoint("pc", "PC", "192.168.1.20", 26789, "token")
    private fun snapshot(id: String = "one") = EchoRemotePlaybackSnapshot(
        state = EchoRemotePlaybackState.Playing,
        track = EchoRemoteTrack(id, "Song", "Artist", null, null, 60_000), durationMs = 60_000)
    private suspend fun eventually(condition: () -> Boolean) { withTimeout(2_000) { while (!condition()) delay(5) } }

    @Test fun newestSeekWinsAndOldSnapshotsDoNotPullTheUiBack() = runBlocking {
        val owner = CoroutineScope(coroutineContext + SupervisorJob())
        val gate = CompletableDeferred<Unit>()
        var actual = snapshot()
        val commands = mutableListOf<EchoRemoteCommand>()
        var oldAcknowledged = false
        lateinit var session: EchoRemoteControlSession
        val transport = object : EchoLinkTransport by FakeEchoLinkTransport() {
            override suspend fun fetchStatus(endpoint: EchoRemoteEndpoint): EchoLinkStatusResponse {
                gate.await(); return EchoLinkStatusResponse("PC", actual)
            }
            override suspend fun sendCommand(endpoint: EchoRemoteEndpoint, command: EchoRemoteCommand): EchoLinkStatusResponse {
                commands += command
                actual = actual.copy(positionMs = (command as EchoRemoteCommand.SeekTo).positionMs)
                return EchoLinkStatusResponse("PC", actual)
            }
        }
        session = EchoRemoteControlSession(owner, transport, { _, response -> session.observe(response.playback!!) }, {}, { _, _ -> }, {})
        try {
            session.bind(endpoint); session.observe(actual)
            session.request(EchoRemoteCommand.SeekTo(1_000)) { oldAcknowledged = true }
            session.request(EchoRemoteCommand.SeekTo(7_000)) {}
            session.observe(actual)
            assertEquals(7_000L, session.projected().positionMs)
            gate.complete(Unit)
            eventually { !session.pending }
            assertEquals(listOf(EchoRemoteCommand.SeekTo(7_000)), commands)
            assertFalse(oldAcknowledged)
        } finally { owner.cancel() }
    }

    @Test fun anAmbiguousToggleIsNeverAutomaticallySentAgain() = runBlocking {
        val owner = CoroutineScope(coroutineContext + SupervisorJob())
        var calls = 0
        val notices = mutableListOf<String>()
        lateinit var session: EchoRemoteControlSession
        val transport = object : EchoLinkTransport by FakeEchoLinkTransport() {
            override suspend fun fetchStatus(endpoint: EchoRemoteEndpoint) = EchoLinkStatusResponse("PC", snapshot())
            override suspend fun sendCommand(endpoint: EchoRemoteEndpoint, command: EchoRemoteCommand): EchoLinkStatusResponse? {
                calls++; throw java.io.IOException("response lost after server applied toggle")
            }
        }
        session = EchoRemoteControlSession(owner, transport, { _, response -> session.observe(response.playback!!) }, {}, { _, _ -> }, { notices += it }, retryDelays = listOf(1))
        try {
            session.bind(endpoint); session.observe(snapshot())
            session.request(EchoRemoteCommand.PlayPause) {}
            eventually { notices.contains("uncertain") && !session.pending }
            delay(20)
            assertEquals(1, calls)
        } finally { owner.cancel() }
    }

    @Test fun changingReceiverCancelsAnInFlightReadAndDoesNotReplayItsSeek() = runBlocking {
        val owner = CoroutineScope(coroutineContext + SupervisorJob())
        val gate = CompletableDeferred<Unit>()
        var commands = 0
        lateinit var session: EchoRemoteControlSession
        val transport = object : EchoLinkTransport by FakeEchoLinkTransport() {
            override suspend fun fetchStatus(endpoint: EchoRemoteEndpoint): EchoLinkStatusResponse { gate.await(); return EchoLinkStatusResponse("PC", snapshot()) }
            override suspend fun sendCommand(endpoint: EchoRemoteEndpoint, command: EchoRemoteCommand): EchoLinkStatusResponse? { commands++; return null }
        }
        session = EchoRemoteControlSession(owner, transport, { _, response -> session.observe(response.playback!!) }, {}, { _, _ -> }, {})
        try {
            session.bind(endpoint); session.observe(snapshot())
            session.request(EchoRemoteCommand.SeekTo(5_000)) {}
            yield()
            session.bind(endpoint.copy(id = "other", host = "192.168.1.21")); session.observe(snapshot("two"))
            gate.complete(Unit); delay(20)
            assertFalse(session.pending)
            assertEquals(0, commands)
            assertEquals("two", session.projected().track?.id)
        } finally { owner.cancel() }
    }

    @Test fun anExpiredBackgroundIntentDoesNotPlayWhenTheAppReturns() = runBlocking {
        val owner = CoroutineScope(coroutineContext + SupervisorJob())
        val gate = CompletableDeferred<Unit>()
        var time = 0L
        var calls = 0
        val notices = mutableListOf<String>()
        lateinit var session: EchoRemoteControlSession
        val transport = object : EchoLinkTransport by FakeEchoLinkTransport() {
            override suspend fun fetchStatus(endpoint: EchoRemoteEndpoint): EchoLinkStatusResponse { gate.await(); return EchoLinkStatusResponse("PC", snapshot()) }
            override suspend fun sendCommand(endpoint: EchoRemoteEndpoint, command: EchoRemoteCommand): EchoLinkStatusResponse? { calls++; return null }
        }
        session = EchoRemoteControlSession(owner, transport, { _, response -> session.observe(response.playback!!) }, {}, { _, _ -> }, { notices += it }, now = { time }, lifetimeMs = 100)
        try {
            session.bind(endpoint); session.observe(snapshot())
            session.request(EchoRemoteCommand.PlayPause) {}
            yield(); session.setActive(false)
            time = 101
            session.setActive(true); gate.complete(Unit); delay(20)
            assertFalse(session.pending); assertEquals(0, calls); assertTrue("expired" in notices)
        } finally { owner.cancel() }
    }

    @Test fun noticesAreBoundedAndDifferentTypesOrSessionsRemainVisible() {
        var time = 0L
        val gate = EchoConnectionNoticeGate { time }
        assertTrue(gate.accept("network")); assertFalse(gate.accept("network"))
        assertTrue(gate.accept("auth"))
        time = 1_800; assertTrue(gate.accept("network"))
        gate.clear(); assertTrue(gate.accept("network"))
    }
}
