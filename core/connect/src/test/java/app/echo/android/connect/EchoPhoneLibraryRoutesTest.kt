package app.echo.android.connect

import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EchoPhoneLibraryRoutesTest {
    private val token = "0123456789abcdef0123456789abcdef"
    private val row = EchoLinkCastPublication("unused-local-publication", "song/一", "content://private/1", title = "Song", artist = "Artist")

    @Test fun requiresCapabilityAndClampsPagingWithoutLeakingLocalUris() {
        val calls = AtomicInteger()
        val routes = EchoPhoneLibraryRoutes(token, "http://192.168.1.5:1000", { query, offset, limit ->
            calls.incrementAndGet()
            assertEquals("a b", query); assertEquals(100, offset); assertEquals(100, limit)
            EchoPhoneLibraryPage(listOf(row), 201)
        }, { row.takeIf { candidate -> candidate.trackId == it } })
        val path = "/echo-link/phone/v1/library/tracks?page=2&pageSize=500&q=a%20b"
        assertEquals(401, routes.metadata(path, emptyMap())!!.status)
        assertEquals(401, routes.metadata(path, mapOf("authorization" to "Bearer $token", "origin" to "http://evil"))!!.status)
        assertEquals(0, calls.get())
        val response = routes.metadata(path, mapOf("authorization" to "Bearer $token"))!!
        assertFalse(response.body.contains("content://"))
        assertEquals(201, JSONObject(response.body).getInt("totalCount"))
        val url = JSONObject(response.body).getJSONArray("tracks").getJSONObject(0).getString("streamUrl")
        assertEquals(row, routes.publication(java.net.URI(url).rawPath)!!.first)
        assertNull(routes.publication(java.net.URI(url.replace(token, "bad-token")).rawPath))
    }

    @Test fun servesOriginalRangeBytesAndStopRevokesThePublishedStream() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val routes = EchoPhoneLibraryRoutes(token, "http://127.0.0.1", { _, _, _ -> EchoPhoneLibraryPage(listOf(row), 1) }, { row })
        val server = EchoLinkCastServer(EchoLinkCastBodyFactory { uri, start ->
            assertEquals(row.uri, uri)
            EchoLinkCastBody(bytes.inputStream().apply { skip(start) }, bytes.size.toLong(), "audio/mpeg")
        }, bindHost = "127.0.0.1", metadata = routes::metadata, resolvePublication = routes::publication)
        try {
            val port = server.start()
            val id = Base64.getUrlEncoder().withoutPadding().encodeToString(row.trackId.toByteArray())
            val request = Request.Builder().url("http://127.0.0.1:$port/echo-link/phone-stream/$token/$id")
                .header("Range", "bytes=2-3").build()
            OkHttpClient().newCall(request).execute().use {
                assertEquals(206, it.code)
                assertEquals("bytes 2-3/5", it.header("Content-Range"))
                assertArrayEquals(byteArrayOf(3, 4), it.body!!.bytes())
            }
            server.stop()
            assertFalse(server.isRunning)
            assertEquals(0, server.localPort)
        } finally { server.stop() }
    }
}
