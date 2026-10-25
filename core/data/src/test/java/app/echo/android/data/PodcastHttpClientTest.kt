package app.echo.android.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PodcastHttpClientTest {
    @Test fun redirectsAndConditionalRequestsPreserveValidators() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/feed") { exchange ->
            exchange.responseHeaders.add("Location", "/actual")
            exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        server.createContext("/actual") { exchange ->
            if (exchange.requestHeaders.getFirst("If-None-Match") == "\"v1\"") exchange.sendResponseHeaders(304, -1)
            else {
                val body = "<rss version=\"2.0\"><channel><title>Test</title></channel></rss>".toByteArray()
                exchange.responseHeaders.add("ETag", "\"v1\"")
                exchange.sendResponseHeaders(200, body.size.toLong()); exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        server.start()
        try {
            val client = PodcastHttpClient("test")
            val url = "http://127.0.0.1:${server.address.port}/feed"
            val first = client.fetch(url, null, null)
            assertEquals("\"v1\"", first.etag)
            assertTrue(first.finalUrl.endsWith("/actual"))
            assertNotNull(first.bytes)
            assertNull(client.fetch(url, first.etag, null).bytes)
        } finally { server.stop(0) }
    }

    @Test fun rejectsCredentialRedirectAndHttpFailure() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "https://user:secret@example.com/feed")
            exchange.sendResponseHeaders(302, -1); exchange.close()
        }
        server.createContext("/error") { exchange -> exchange.sendResponseHeaders(503, -1); exchange.close() }
        server.start()
        try {
            val client = PodcastHttpClient("test")
            for (path in listOf("redirect", "error")) {
                assertTrue(runCatching { client.fetch("http://127.0.0.1:${server.address.port}/$path", null, null) }.isFailure)
            }
        } finally { server.stop(0) }
    }
}
