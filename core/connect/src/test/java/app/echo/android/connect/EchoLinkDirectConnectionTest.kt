package app.echo.android.connect

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EchoLinkDirectConnectionTest {
    @Test
    fun pairedPcUsesExistingV2PlaybackOrderActionWhileDirectPcUsesV1() = runBlocking {
        val requests = LinkedBlockingQueue<Pair<String, org.json.JSONObject>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/echo-link/") { exchange ->
            requests.add(exchange.requestURI.path to org.json.JSONObject(exchange.requestBody.bufferedReader().use { it.readText() }))
            val body = if (exchange.requestURI.path.endsWith("/actions/playback")) """{"ok":true}"""
                else """{"playback":{"playbackOrder":"shuffle"}}"""
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val endpoint = EchoPairingParser.parseManual("127.0.0.1:${server.address.port}")!!
            val command = app.echo.android.model.connect.EchoRemoteCommand.SetPlaybackOrder(
                app.echo.android.model.connect.EchoRemotePlaybackOrder.Shuffle)
            val transport = OkHttpEchoLinkTransport()
            assertNull(transport.sendCommand(endpoint.copy(token = "paired-token", supportsV2Events = true), command))
            val paired = requests.poll(2, TimeUnit.SECONDS)!!
            assertEquals("/echo-link/v2/actions/playback", paired.first)
            assertEquals("setPlaybackOrder", paired.second.getString("action"))
            assertEquals("shuffle", paired.second.getString("mode"))
            org.junit.Assert.assertTrue(paired.second.getString("requestId").isNotBlank())
            assertEquals(command.mode, transport.sendCommand(endpoint, command)?.playback?.playbackOrder)
            val direct = requests.poll(2, TimeUnit.SECONDS)!!
            assertEquals("/echo-link/v1/playback/command", direct.first)
            assertEquals("setPlaybackOrder", direct.second.getString("command"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun directStatusOmitsBearerWhileLegacyStatusRetainsIt() = runBlocking {
        val requests = LinkedBlockingQueue<Map<String, String?>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/echo-link/v1/status") { exchange ->
            requests.add(mapOf(
                "direct" to exchange.requestHeaders.getFirst("X-ECHO-Link-Direct"),
                "auth" to exchange.requestHeaders.getFirst("Authorization"),
                "version" to exchange.requestHeaders.getFirst("X-ECHO-Link-Version"),
            ))
            val body = """{"device":{"name":"Test PC"},"playback":{"state":"paused"}}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
        server.start()
        try {
            val transport = OkHttpEchoLinkTransport()
            val direct = EchoPairingParser.parseManual("127.0.0.1:${server.address.port}")!!
            assertEquals("Test PC", transport.fetchStatus(direct).deviceName)
            val first = requests.poll(2, TimeUnit.SECONDS)!!
            assertEquals("1", first["direct"])
            assertEquals("1", first["version"])
            assertNull(first["auth"])
            transport.fetchStatus(direct.copy(token = "legacy-access-token"))
            val second = requests.poll(2, TimeUnit.SECONDS)!!
            assertEquals("Bearer legacy-access-token", second["auth"])
            assertNull(second["direct"])
        } finally {
            server.stop(0)
        }
    }
}
