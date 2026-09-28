package app.echo.android.listening

import app.echo.android.model.listening.EchoListeningAudio
import app.echo.android.model.listening.EchoListeningConnection
import app.echo.android.model.listening.EchoListeningError
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoListeningControllerTest {
    @Test fun joinsFromAnInviteAndPlaysTheCurrentEpoch() = runBlocking {
        val transport = FakeListeningTransport()
        val sink = RecordingSink()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val controller = EchoListeningController(scope, transport, sink)
        val code = inviteCode("wss://listen.example", "room-1")
        val joining = scope.launch { controller.connect(code, "Phone", null) }
        reply(transport, "hello", helloResult())
        reply(transport, "rooms", JSONArray())
        val join = JSONObject(transport.sent.receive())
        assertEquals("room-1", join.getJSONObject("data").getString("roomId"))
        transport.push(result(join.getString("id"), roomJson(epoch = 9)))
        joining.join()
        assertEquals("room-1", controller.state.value.room?.id)
        assertEquals(9L, sink.started.receive())
        transport.pushBinary(frame(epoch = 4, sequence = 0, payload = ByteArray(4)))
        transport.pushBinary(frame(epoch = 9, sequence = 0, payload = ByteArray(4)))
        assertEquals(9L, sink.offered.receive().epoch)
        scope.cancel()
    }

    @Test fun refusesAServerWithoutFixedAudio() = runBlocking {
        val transport = FakeListeningTransport()
        val sink = RecordingSink()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val controller = EchoListeningController(scope, transport, sink)
        val connecting = scope.launch { controller.connect("wss://listen.example", "Phone", null) }
        reply(transport, "hello", helloResult(bitrate = 128_000))
        connecting.join()
        assertEquals(EchoListeningError.ServerTooOld, controller.state.value.error)
        assertTrue(sink.started.isEmpty)
        scope.cancel()
    }

    @Test fun keepsWrongPasswordOnTheConnection() = runBlocking {
        val transport = FakeListeningTransport()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val controller = EchoListeningController(scope, transport, RecordingSink())
        val connecting = scope.launch { controller.connect("wss://listen.example", "Phone", null) }
        reply(transport, "hello", helloResult())
        reply(transport, "rooms", JSONArray().put(JSONObject().put("id", "room-1").put("name", "Room").put("locked", true)))
        connecting.join()
        val joining = scope.launch { controller.join("room-1", password = null, invitation = null) }
        val join = JSONObject(transport.sent.receive())
        transport.push(JSONObject().put("id", join.getString("id")).put("error", "wrong_password").toString())
        joining.join()
        assertEquals(EchoListeningError.WrongPassword, controller.state.value.error)
        assertEquals("room-1", controller.state.value.passwordRoomId)
        assertEquals("wss://listen.example", controller.state.value.server)
        scope.cancel()
    }

    @Test fun resumesAfterARemoteClose() = runBlocking {
        val transport = FakeListeningTransport()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val controller = EchoListeningController(scope, transport, RecordingSink(), wait = {})
        val connecting = scope.launch { controller.connect("wss://listen.example", "Phone", null) }
        reply(transport, "hello", helloResult())
        reply(transport, "rooms", JSONArray())
        connecting.join()
        transport.remoteClose()
        val hello = JSONObject(transport.sent.receive())
        assertEquals("hello", hello.getString("type"))
        assertEquals("resume", hello.getJSONObject("data").getString("resumeToken"))
        transport.push(result(hello.getString("id"), helloResult()))
        reply(transport, "rooms", JSONArray())
        assertEquals(EchoListeningConnection.Online, controller.state.value.connection)
        scope.cancel()
    }

    private suspend fun reply(transport: FakeListeningTransport, type: String, result: Any) {
        val request = JSONObject(transport.sent.receive())
        assertEquals(type, request.getString("type"))
        transport.push(result(request.getString("id"), result))
    }

    private fun result(id: String, result: Any): String = JSONObject().put("id", id).put("result", result).toString()

    private fun helloResult(bitrate: Int = 256_000): JSONObject = JSONObject()
        .put("protocol", 1)
        .put("peerId", "peer")
        .put("resumeToken", "resume")
        .put("name", "ECHO")
        .put("capabilities", JSONObject().put("fixedAudioBitrate", bitrate).put("chat", true).put("programmeState", true))

    private fun roomJson(epoch: Long): JSONObject = JSONObject()
        .put("id", "room-1")
        .put("name", "Room")
        .put("streamEpoch", epoch)
        .put("programmeState", "playing")
        .put("title", "Song")
        .put("members", JSONArray().put(JSONObject().put("id", "peer").put("name", "Phone").put("online", true)))

    private fun inviteCode(server: String, roomId: String): String {
        val payload = JSONObject().put("server", server).put("roomId", roomId).toString()
        return "echo-listen:" + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
    }
}

private class FakeListeningTransport : EchoListeningTransport {
    private val events = MutableSharedFlow<EchoListeningInbound>(extraBufferCapacity = 16)
    override val incoming: Flow<EchoListeningInbound> = events
    private var binaryListener: (ByteArray) -> Unit = {}
    val sent = Channel<String>(capacity = 8)
    override fun setBinaryListener(listener: (ByteArray) -> Unit) {
        binaryListener = listener
    }
    override suspend fun open(url: String) = Unit
    override suspend fun send(text: String) {
        sent.send(text)
    }
    override fun close() = Unit
    fun remoteClose() {
        events.tryEmit(EchoListeningInbound.Closed("connection_closed"))
    }
    fun push(body: String) {
        check(events.tryEmit(EchoListeningInbound.Text(body)))
    }
    fun pushBinary(body: ByteArray) {
        binaryListener(body)
    }
}

private class RecordingSink : EchoListeningAudioSink {
    override val phase = MutableStateFlow(EchoListeningAudio.Off)
    val started = Channel<Long>(capacity = 4)
    val offered = Channel<EchoListeningPacket>(capacity = 4)
    override fun startEpoch(epoch: Long) {
        phase.value = EchoListeningAudio.Buffering
        started.trySend(epoch)
    }
    override fun offer(packet: EchoListeningPacket) {
        offered.trySend(packet)
    }
    override fun setVolume(volume: Float) = Unit
    override fun stop() {
        phase.value = EchoListeningAudio.Off
    }
}
