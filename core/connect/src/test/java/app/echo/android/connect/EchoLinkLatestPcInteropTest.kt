package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteEndpoint
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Wire shapes verified against echosteam a8f1bd0, including the two different queue APIs. */
class EchoLinkLatestPcInteropTest {
    @Test
    fun eventHandshakeRetainsCredentialRejectionStatus() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/echo-link/v2/events") { exchange ->
            val bytes = """{"error":{"code":"unauthorized"}}""".toByteArray()
            exchange.sendResponseHeaders(401, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        val endpoint = EchoRemoteEndpoint("pc", "PC", "127.0.0.1", server.address.port, "revoked-token")
        val closed = kotlinx.coroutines.CompletableDeferred<Throwable?>()
        val subscription = OkHttpEchoLinkTransport().subscribeEvents(endpoint,
            EchoLinkEventTicket("ticket", endpoint.resolveEventsUrl("/echo-link/v2/events?ticket=ticket")!!),
            onEvent = { fail("Rejected events must not produce snapshots") }, onClosed = { closed.complete(it) })
        try {
            val error = kotlinx.coroutines.withTimeout(2_000) { closed.await() }
            assertTrue(error is EchoLinkHttpException)
            assertEquals(401, (error as EchoLinkHttpException).statusCode)
        } finally { subscription.cancel(); server.stop(0) }
    }

    @Test
    fun pairedAndDirectClientsReadThePcQueueWithoutAssumingAHundredRows() = runBlocking {
        val requests = LinkedBlockingQueue<String>()
        val credentials = LinkedBlockingQueue<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/echo-link/") { exchange ->
            val path = exchange.requestURI.path
            requests.add(path)
            credentials.add(exchange.requestHeaders.getFirst("Authorization") ?: "direct")
            val response = when (path) {
                "/echo-link/v2/pair" -> {
                    val body = JSONObject(exchange.requestBody.bufferedReader().use { it.readText() })
                    assertEquals("android", body.getString("platform"))
                    """{"accessToken":"pc-access-token","scopes":["companion:v1","events:read"]}"""
                }
                "/echo-link/v1/status" -> """{"device":{"name":"Latest PC"},"playback":{"state":"paused"}}"""
                "/echo-link/v2/queue" -> """{"revision":7,"currentQueueId":"occurrence-24","page":2,"pageSize":24,"total":80,"items":[{"queueId":"occurrence-24","trackId":"song","title":"Song","artist":"Artist","durationMs":1000}]}"""
                "/echo-link/v1/queue" -> """{"currentQueueId":"occurrence-50","page":2,"pageSize":50,"total":80,"items":[{"queueId":"occurrence-50","id":"song","title":"Song","artist":"Artist","queueIndex":50}]}"""
                "/echo-link/v1/library/tracks" -> """{"totalCount":1,"tracks":[{"id":"song","title":"Song","artist":"Artist"}]}"""
                "/echo-link/v1/library/tracks/song/stream" -> {
                    val body = JSONObject(exchange.requestBody.bufferedReader().use { it.readText() })
                    assertEquals("phone", body.getString("target"))
                    assertEquals(1, body.getInt("sourceRange"))
                    """{"streamUrl":"http://127.0.0.1:${server.address.port}/echo-link/media/ticket","expiresAtEpochMs":1800000000000}"""
                }
                else -> """{"error":"not_found"}"""
            }.toByteArray()
            exchange.sendResponseHeaders(if (path.endsWith("/pair")) 201 else 200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
            exchange.close()
        }
        server.start()
        try {
            val transport = OkHttpEchoLinkTransport()
            val direct = EchoPairingParser.parseManual("127.0.0.1:${server.address.port}")!!
            val paired = transport.completePairing(direct.copy(pairingId = "pair", pairingSecret = "secret"))
            assertEquals("Latest PC", transport.fetchStatus(paired).deviceName)
            val v2 = transport.fetchPlaybackQueue(paired, 2)
            assertEquals(80, v2.totalCount)
            assertEquals(24, v2.offset)
            assertEquals(24, v2.pageSize)
            assertEquals(7L, v2.revision)
            assertEquals("song", v2.items.single().id)
            assertEquals(24, v2.items.single().queueIndex)
            assertFalse(v2.supportsEditing)
            val v1 = transport.fetchPlaybackQueue(direct, 2)
            assertEquals(80, v1.totalCount)
            assertEquals(50, v1.offset)
            assertEquals(50, v1.pageSize)
            assertNull(v1.revision)
            assertFalse(v1.supportsEditing)
            assertEquals("song", transport.fetchTracks(paired, "", 1, 30).tracks.single().id)
            assertTrue(transport.resolveStream(paired, "song").streamUrl.endsWith("/echo-link/media/ticket"))
            assertEquals(listOf("/echo-link/v2/pair", "/echo-link/v1/status", "/echo-link/v2/queue",
                "/echo-link/v1/queue", "/echo-link/v1/library/tracks", "/echo-link/v1/library/tracks/song/stream"), requests.toList())
            assertEquals(listOf("direct", "Bearer pc-access-token", "Bearer pc-access-token", "direct",
                "Bearer pc-access-token", "Bearer pc-access-token"), credentials.toList())
        } finally { server.stop(0) }
    }

    @Test
    fun editableCompanionFallbackIsUsedOnlyForMissingRoutes() = runBlocking {
        val requests = LinkedBlockingQueue<String>()
        var reject = false
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/echo-link/") { exchange ->
            val path = exchange.requestURI.path
            requests.add(path)
            val code = if (reject) 401 else if (path == "/echo-link/v1/queue") 404 else 200
            val body = (if (code == 200) """{"items":[],"totalCount":0,"revision":3}"""
                else """{"error":{"code":"unauthorized","message":"unauthorized"}}""").toByteArray()
            exchange.sendResponseHeaders(code, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
        server.start()
        try {
            val endpoint = EchoRemoteEndpoint("pc", "PC", "127.0.0.1", server.address.port, "paired-token")
            val transport = OkHttpEchoLinkTransport()
            val result = transport.fetchPlaybackQueue(endpoint, 1)
            assertTrue(result.supportsEditing)
            assertEquals(100, result.pageSize)
            assertEquals(listOf("/echo-link/v1/queue", "/echo-link/v1/playback/queue"), requests.toList())
            requests.clear()
            reject = true
            try {
                transport.fetchPlaybackQueue(endpoint, 1)
                fail("Rejected credentials must not fall back")
            } catch (error: EchoLinkHttpException) {
                assertEquals(401, error.statusCode)
                assertEquals("unauthorized", error.message)
            }
            assertEquals(listOf("/echo-link/v1/queue"), requests.toList())
        } finally { server.stop(0) }
    }
}
