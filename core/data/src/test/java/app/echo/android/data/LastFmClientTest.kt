package app.echo.android.data

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LastFmClientTest {
    private val credentials = LastFmCredentials("custom-key", "custom-secret", "session")

    private fun <T> server(response: String, block: (LastFmClient, () -> Map<String, String>) -> T): T {
        var params = emptyMap<String, String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val encoded = if (exchange.requestMethod == "GET") exchange.requestURI.rawQuery
                else exchange.requestBody.bufferedReader().use { it.readText() }
            params = encoded.split('&').associate {
                val pair = it.split('=', limit = 2)
                URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
            }
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { return block(LastFmClient("http://127.0.0.1:${server.address.port}/")) { params } }
        finally { server.stop(0) }
    }

    @Test fun loveSignsCustomCredentialsAndUnicodeMetadata() = server("{}") { client, params -> runBlocking {
        client.love(credentials, "歌 & 名", "艺人").getOrThrow()
        val sent = params()
        assertEquals("track.love", sent["method"])
        assertEquals("歌 & 名", sent["track"])
        assertEquals("custom-key", sent["api_key"])
        val signature = sent.filterKeys { it !in setOf("api_sig", "format") }.toSortedMap()
            .entries.joinToString("") { it.key + it.value } + "custom-secret"
        val expected = MessageDigest.getInstance("MD5").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(expected, sent["api_sig"])
    } }

    @Test fun httpSuccessWithInvalidSessionIsStillAnAuthorizationFailure() = server("""{"error":9,"message":"Invalid session"}""") { client, _ -> runBlocking {
        val error = client.love(credentials, "Song", "Artist").exceptionOrNull() as LastFmApiException
        assertTrue(error.requiresAuthorization)
        assertFalse(error.retryable)
    } }

    @Test fun ignoredScrobbleDoesNotBecomeAReportedSuccess() = server("""{"scrobbles":{"@attr":{"accepted":"0"},"scrobble":{"ignoredMessage":{"code":"1","#text":"Artist filtered"}}}}""") { client, _ -> runBlocking {
        val error = client.scrobble(credentials, LastFmTrack("id", "Song", "Artist", null, 180000), 1).exceptionOrNull() as LastFmApiException
        assertEquals(101, error.code)
        assertFalse(error.retryable)
    } }

    @Test fun importsOverallCountsWithExplicitPagination() = server("""{"toptracks":{"track":[{"name":"Song","artist":{"name":"Artist"},"playcount":"42"}],"@attr":{"totalPages":"3"}}}""") { client, params -> runBlocking {
        val page = client.importPage(credentials, "listener", false, 2)
        assertEquals(listOf(LastFmImportTrack("Song", "Artist", false, 42)), page.tracks)
        assertEquals(3, page.totalPages)
        assertEquals("overall", params()["period"])
        assertEquals("2", params()["page"])
        assertFalse(params().containsKey("sk"))
        assertFalse(params().containsKey("api_sig"))
    } }
}
