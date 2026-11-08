package app.echo.android.connect

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.LinkedBlockingQueue
import kotlinx.coroutines.runBlocking

class EchoLinkLyricsParseTest {
    @Test
    fun legacyLyricsPreferenceIsRememberedOnlyForTheMatchingCredentials() = runBlocking {
        val paths = LinkedBlockingQueue<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/echo-link/v1/") { exchange ->
            val path = exchange.requestURI.path
            paths.add(path)
            val available = path.startsWith("/echo-link/v1/lyrics/") ||
                exchange.requestHeaders.getFirst("Authorization") == "Bearer modern-token"
            val bytes = (if (available) "[00:01.00]lyrics" else "missing").toByteArray()
            exchange.sendResponseHeaders(if (available) 200 else 404, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            val transport = OkHttpEchoLinkTransport()
            val endpoint = EchoPairingParser.parseManual("127.0.0.1:${server.address.port}", "legacy-token")!!
            assertEquals("[00:01.00]lyrics", transport.fetchLyrics(endpoint, "one")?.rawText)
            assertEquals("[00:01.00]lyrics", transport.fetchLyrics(endpoint, "two")?.rawText)
            assertEquals("[00:01.00]lyrics", transport.fetchLyrics(endpoint.copy(token = "modern-token"), "three")?.rawText)
            assertEquals(listOf("/echo-link/v1/library/tracks/one/lyrics", "/echo-link/v1/lyrics/one",
                "/echo-link/v1/lyrics/two", "/echo-link/v1/library/tracks/three/lyrics"), paths.toList())
        } finally { server.stop(0) }
    }

    @Test
    fun prefersSyncedLyricsOverPlainLyrics() {
        val json = JSONObject(
            """
            {
              "lyrics": "unsynced line",
              "syncedLyrics": "[00:01.00]synced line"
            }
            """.trimIndent(),
        )
        assertEquals("[00:01.00]synced line", echoLinkLyricsRawText(json))
    }

    @Test
    fun fallsBackToPlainLyricsWhenSyncedIsMissing() {
        val json = JSONObject(
            """
            { "lyrics": "plain" }
            """.trimIndent(),
        )
        assertEquals("plain", echoLinkLyricsRawText(json))
    }
}
