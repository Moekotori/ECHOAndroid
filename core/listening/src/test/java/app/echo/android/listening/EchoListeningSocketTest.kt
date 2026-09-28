package app.echo.android.listening

import app.echo.android.model.listening.EchoListeningAudio
import app.echo.android.model.listening.EchoListeningConnection
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class EchoListeningSocketTest {
    @Test fun helloJoinAndAudioUseTheRealSocket() = runBlocking {
        val server = MockWebServer()
        val remote = Channel<WebSocket>(capacity = 1)
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                remote.trySend(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val json = JSONObject(text)
                val result = when (json.getString("type")) {
                    "hello" -> JSONObject()
                        .put("protocol", 1)
                        .put("peerId", "peer")
                        .put("resumeToken", "resume")
                        .put("name", "ECHO")
                        .put(
                            "capabilities",
                            JSONObject().put("fixedAudioBitrate", 256_000).put("chat", true).put("programmeState", true),
                        )
                    "rooms" -> JSONArray()
                    "join" -> JSONObject()
                        .put("id", "room-1")
                        .put("name", "Room")
                        .put("streamEpoch", 9)
                        .put("programmeState", "playing")
                        .put("title", "Song")
                    else -> return
                }
                webSocket.send(JSONObject().put("id", json.getString("id")).put("result", result).toString())
            }
        }))
        server.start()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val sink = SocketRecordingSink()
        val transport = OkHttpListeningTransport()
        val controller = EchoListeningController(scope, transport, sink, wait = {})
        try {
            val origin = "ws://127.0.0.1:${server.port}"
            val payload = JSONObject().put("server", origin).put("roomId", "room-1").toString()
            val code = "echo-listen:" + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())
            val joining = scope.launch { controller.connect(code, "Phone", null) }
            val socket = remote.receive()
            joining.join()
            assertEquals(EchoListeningConnection.Online, controller.state.value.connection)
            assertEquals("room-1", controller.state.value.room?.id)
            assertEquals(9L, sink.started.receive())
            socket.send(frame(epoch = 9, sequence = 1, payload = ByteArray(8) { 2 }).toByteString())
            assertEquals(9L, sink.offered.receive().epoch)
        } finally {
            transport.close()
            scope.cancel()
            server.shutdown()
        }
    }
}

private class SocketRecordingSink : EchoListeningAudioSink {
    override val phase = MutableStateFlow(EchoListeningAudio.Off)
    val started = Channel<Long>(capacity = 2)
    val offered = Channel<EchoListeningPacket>(capacity = 2)
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
