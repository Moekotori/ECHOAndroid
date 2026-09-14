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
