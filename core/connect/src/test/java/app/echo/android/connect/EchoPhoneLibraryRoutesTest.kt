package app.echo.android.connect

import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import app.echo.android.model.connect.EchoPhoneCatalogEntry
import app.echo.android.model.connect.EchoPhoneCatalogPage

class EchoPhoneLibraryRoutesTest {
    private val token = "0123456789abcdef0123456789abcdef"
    private val row = EchoLinkCastPublication("unused-local-publication", "song/一", "content://private/1", title = "Song", artist = "Artist")

    @Test fun catalogsPhoneCategoriesWithBoundedPagesAndNoPrivateMediaUris() {
        val routes = EchoPhoneLibraryRoutes(token, "http://192.168.1.5:1000",
            { _, _, _ -> EchoPhoneLibraryPage(emptyList(), 0) }, { null }, catalog = { query ->
                assertEquals("songs", query.kind); assertEquals("album", query.parentKind); assertEquals("album-id", query.parentId)
                EchoPhoneCatalogPage(listOf(EchoPhoneCatalogEntry("track", row.trackId, row.title, canPlay = true)), 1, query.page)
            })
        val path = "/echo-link/phone/v1/library/catalog?kind=songs&parentKind=album&parentId=album-id&page=2"
        assertEquals(401, routes.metadata(path, emptyMap())!!.status)
        val response = routes.metadata(path, mapOf("authorization" to "Bearer $token"))!!
        val catalog = JSONObject(response.body).getJSONObject("catalog")
        assertEquals(2, catalog.getInt("page")); assertEquals(50, catalog.getInt("pageSize"))
        assertFalse(response.body.contains("content://")); assertFalse(response.body.contains(token))
        assertEquals(400, routes.metadata(path.replace("page=2", "page=100001"), mapOf("authorization" to "Bearer $token"))!!.status)
    }

    @Test fun returnsConfirmedArtistAlbumScopeToPreventUnfilteredLegacyResults() {
        val routes = EchoPhoneLibraryRoutes(token, "http://192.168.1.5:1000",
            { _, _, _ -> EchoPhoneLibraryPage(emptyList(), 0) }, { null }, catalog = { query ->
                assertEquals("albums", query.kind)
                EchoPhoneCatalogPage(emptyList(), 0, query.page, query.parentKind, query.parentId)
            })
        val response = routes.metadata("/echo-link/phone/v1/library/catalog?kind=albums&parentKind=artist&parentId=artist-id",
            mapOf("authorization" to "Bearer $token"))!!
        val parent = JSONObject(response.body).getJSONObject("catalog").getJSONObject("parent")
        assertEquals("artist", parent.getString("kind")); assertEquals("artist-id", parent.getString("id"))
    }

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

    @Test fun clampsOversizedRangesForAudioAndArtworkIncludingHead() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val artRow = row.copy(artworkUrl = "file:///cover.jpg")
        val routes = EchoPhoneLibraryRoutes(token, "http://127.0.0.1",
            { _, _, _ -> EchoPhoneLibraryPage(listOf(artRow), 1) }, { artRow })
        val server = EchoLinkCastServer(EchoLinkCastBodyFactory { _, start ->
            EchoLinkCastBody(bytes.inputStream().apply { skip(start) }, bytes.size.toLong(), "audio/mpeg")
        }, bindHost = "127.0.0.1", resolvePublication = routes::publication)
        try {
            val port = server.start()
            val id = Base64.getUrlEncoder().withoutPadding().encodeToString(row.trackId.toByteArray())
            val client = OkHttpClient()
            for (route in listOf("phone-stream", "phone-art")) {
                val url = "http://127.0.0.1:$port/echo-link/$route/$token/$id"
                for (method in listOf("GET", "HEAD")) {
                    client.newCall(Request.Builder().url(url).method(method, null)
                        .header("Range", "bytes=2-999").build()).execute().use {
                        assertEquals(206, it.code)
                        assertEquals("bytes 2-4/5", it.header("Content-Range"))
                        assertEquals("3", it.header("Content-Length"))
                        assertArrayEquals(if (method == "HEAD") byteArrayOf() else byteArrayOf(3, 4, 5), it.body!!.bytes())
                    }
                }
                client.newCall(Request.Builder().url(url).header("Range", "bytes=5-999").build()).execute().use {
                    assertEquals(416, it.code)
                    assertEquals("bytes */5", it.header("Content-Range"))
                }
                client.newCall(Request.Builder().url(url).header("Range", "invalid").build()).execute().use {
                    assertEquals(416, it.code)
                }
            }
        } finally { server.stop() }
    }
}
