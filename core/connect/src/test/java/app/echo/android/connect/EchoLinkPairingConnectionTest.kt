package app.echo.android.connect

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLEncoder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EchoLinkPairingConnectionTest {
    @Test
    fun pairingLinkConnectsAndReconnectsToPcThatRejectsDirectAccess() = runBlocking {
        val requests = LinkedBlockingQueue<Pair<String, String>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/echo-link/v2/pair") { exchange ->
            requests.add(exchange.requestMethod to exchange.requestBody.bufferedReader().use { it.readText() })
            val body = """{"accessToken":"test-paired-access-token"}""".toByteArray()
            exchange.sendResponseHeaders(201, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
        server.createContext("/echo-link/v1/status") { exchange ->
            val authorized = exchange.requestHeaders.getFirst("Authorization") == "Bearer test-paired-access-token"
            val body = if (authorized) {
                """{"device":{"name":"Paired PC"},"playback":{"state":"paused"}}"""
            } else {
                """{"error":"unauthorized"}"""
            }.toByteArray()
            exchange.sendResponseHeaders(if (authorized) 200 else 401, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
        server.start()
        try {
            val transport = OkHttpEchoLinkTransport()
            val address = "127.0.0.1:${server.address.port}"
            try {
                transport.fetchStatus(EchoPairingParser.parseManual(address)!!)
                fail("A PC requiring pairing must reject a bare address")
            } catch (error: EchoLinkHttpException) {
                assertEquals(401, error.statusCode)
            }

            val pairUri = "echo://pair?version=2&host=127.0.0.1&port=${server.address.port}" +
                "&pairingId=test-pair&secret=test-pairing-secret"
            val link = "http://$address/echo-link/v2/remote#pair=${URLEncoder.encode(pairUri, "UTF-8")}"
            val pending = EchoPairingParser.parseManual(link)!!
            assertTrue(pending.needsV2PairExchange)
            val paired = transport.completePairing(pending)
            val request = requests.poll(2, TimeUnit.SECONDS)!!
            assertEquals("POST", request.first)
            val payload = JSONObject(request.second)
            assertEquals("test-pair", payload.getString("pairingId"))
            assertEquals("test-pairing-secret", payload.getString("secret"))
            assertEquals("android", payload.getString("platform"))
            assertFalse(paired.needsV2PairExchange)
            assertNull(paired.pairingSecret)
            assertEquals("test-paired-access-token", paired.token)
            assertEquals("Paired PC", transport.fetchStatus(paired).deviceName)

            val restored = EchoPairingParser.parseManual(address, paired.token)!!
            assertEquals("Paired PC", transport.fetchStatus(transport.completePairing(restored)).deviceName)
            assertTrue("Reconnect must reuse the access token", requests.isEmpty())
        } finally {
            server.stop(0)
        }
    }
}
