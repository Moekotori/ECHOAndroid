package app.echo.android.connect

import app.echo.android.model.connect.EchoLinkPhonePresenceTrack
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
    fun pairedPhonePresenceUsesOnlyDisplayMetadataAndCanClearIt() = runBlocking {
        val requests = LinkedBlockingQueue<JSONObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/echo-link/v1/status") { exchange ->
            val body = """{"playback":{"state":"paused"},"capabilities":{"phonePresence":true}}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.createContext("/echo-link/v2/phone-presence") { exchange ->
            if (exchange.requestMethod == "PUT" &&
                exchange.requestHeaders.getFirst("Authorization") == "Bearer test-paired-access-token"
            ) {
                requests.add(exchange.requestBody.bufferedReader().use { JSONObject(it.readText()) })
                exchange.sendResponseHeaders(204, -1)
            } else {
                exchange.sendResponseHeaders(401, -1)
            }
            exchange.close()
        }
        server.start()
        try {
            val transport = OkHttpEchoLinkTransport()
            val address = "127.0.0.1:${server.address.port}"
            val direct = EchoPairingParser.parseManual(address)!!
            assertEquals(false, transport.fetchStatus(direct).supportsPhonePresence)
            val paired = direct.copy(token = "test-paired-access-token")
            assertEquals(true, transport.fetchStatus(paired).supportsPhonePresence)

            val boundedTitle = "音".repeat(255)
            val track = EchoLinkPhonePresenceTrack.fromMetadata("${boundedTitle}🎵", " Artist ")
            transport.updatePhonePresence(paired, requireNotNull(track))
            val play = requests.remove()
            assertEquals(1, play.length())
            assertEquals(2, play.getJSONObject("track").length())
            assertEquals(boundedTitle, play.getJSONObject("track").getString("title"))
            assertEquals("Artist", play.getJSONObject("track").getString("artist"))

            transport.updatePhonePresence(paired, null)
            val clear = requests.remove()
            assertEquals(1, clear.length())
            assertTrue(clear.has("track"))
            assertTrue(clear.isNull("track"))
        } finally {
            server.stop(0)
        }
    }

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

            val saved = app.echo.android.model.connect.EchoSavedPcEndpoint("http://$address", "PC",
                supportsV2Events = paired.supportsV2Events, token = paired.token)
            val restored = EchoManualConnectionInput.endpointForConnection(address, paired.token, listOf(saved))!!
            assertTrue(restored.supportsV2Events)
            assertEquals("Paired PC", transport.fetchStatus(transport.completePairing(restored)).deviceName)
            assertTrue("Reconnect must reuse the access token", requests.isEmpty())
        } finally {
            server.stop(0)
        }
    }
}
