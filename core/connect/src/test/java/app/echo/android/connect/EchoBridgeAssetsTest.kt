package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteEndpoint
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

/** Protect real adapter projection, bounded image decoding, and Bridge request admission. */
class EchoBridgeAssetsTest {
    private val endpoint = EchoRemoteEndpoint("bridge", "Bridge", "127.0.0.1", 49234, "", libraryOnly = true)

    @Test fun catalogUsesOpaqueCoverIdsWithoutFetchingImagesOrSendingCredentials() = runBlocking {
        var requests = 0
        val transport = EchoBridgeTransport { request ->
            requests++
            assertEquals("/echo-link/peer/v1/catalog", request.url.encodedPath)
            assertEquals("1", request.header("X-ECHO-Link-Direct"))
            assertNull(request.header("Authorization"))
            val entries = JSONArray().put(JSONObject().put("id", "song").put("title", "Song")
                .put("coverId", "art /:封面").put("sourceProvider", "local").put("canPlay", true))
            if (request.url.queryParameter("kind") == "playlists") {
                entries.put(JSONObject().put("id", "unknown").put("title", "Unknown"))
                entries.put(JSONObject().put("id", "remote").put("title", "Remote").put("sourceProvider", "netease"))
            }
            JSONObject().put("catalog", JSONObject().put("page", 1).put("pageSize", 50)
                .put("total", entries.length()).put("entries", entries))
        }
        val playlists = transport.playlists(endpoint, "", 1).playlists
        assertEquals(listOf("song", "unknown"), playlists.map { it.id })
        val urls = listOf(transport.tracks(endpoint, "", 1).tracks.single().artworkUrl,
            transport.albums(endpoint, "", 1).albums.single().artworkUrl,
            playlists.first().artworkUrl)
        assertEquals(3, requests)
        for (url in urls) {
            assertTrue(EchoBridgeArtwork.handles(url!!))
            assertEquals("art /:封面", url.toHttpUrl().queryParameter("id"))
            assertEquals(49234, url.toHttpUrl().port)
        }
    }

    @Test fun bridgeCatalogWithoutPlaylistProvenanceRemainsBrowsable() = runBlocking {
        val transport = EchoBridgeTransport { request ->
            val page = request.url.queryParameter("page")!!.toInt()
            val entries = JSONArray()
            val ids = if (page == 1) 1..50 else 51..51
            for (id in ids) entries.put(JSONObject().put("kind", "playlist").put("id", "p$id")
                .put("title", "Playlist $id").put("count", 2))
            JSONObject().put("catalog", JSONObject().put("page", page).put("pageSize", 50)
                .put("total", 51).put("entries", entries))
        }
        val first = transport.playlists(endpoint, "", 1)
        assertEquals(50, first.playlists.size)
        assertEquals(2, first.nextPage)
        val last = transport.playlists(endpoint, "", first.nextPage)
        assertEquals("p51", last.playlists.single().id)
        assertEquals(0, last.nextPage)
    }

    @Test fun structuredOnlyLyricsKeepSourceTimingAndRemovedLyricsStayEmpty() = runBlocking {
        val source = JSONObject("""{"kind":"synced","offsetMs":-120,"lines":[
            {"timeMs":1250.5,"text":"First","translation":"译文","words":[{"startMs":1250.5,"endMs":2000,"text":"First"}]}]}""")
        val transport = EchoBridgeTransport { JSONObject().put("lyrics", source) }
        val shared = JSONObject(transport.lyrics(endpoint, "song")!!.rawText)
        assertEquals(-120, shared.getInt("offsetMs"))
        assertEquals(1250.5, shared.getJSONArray("lines").getJSONObject(0).getDouble("timeMs"), 0.0)
        source.put("kind", "empty")
        assertNull(transport.lyrics(endpoint, "song"))
    }

    @Test fun rasterDecoderRejectsOversizeAndActiveContent() {
        val png = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        fun response(bytes: ByteArray, mime: String) = JSONObject().put("image", JSONObject()
            .put("mime", mime).put("bytes", Base64.getEncoder().encodeToString(bytes)))
        assertArrayEquals(png, EchoBridgeArtwork.decode(response(png, "image/png"))!!.bytes)
        assertThrows(IllegalArgumentException::class.java) { EchoBridgeArtwork.decode(response("<svg/>".toByteArray(), "image/svg+xml")) }
        assertThrows(IllegalArgumentException::class.java) { EchoBridgeArtwork.decode(response(ByteArray(256 * 1024 + 1), "image/png")) }
        assertNull(EchoBridgeArtwork.decode(JSONObject().put("image", JSONObject.NULL)))
    }

    @Test fun bridgeQueriesLeaveCapacityForMediaAndReleasePermits() = runBlocking {
        val active = AtomicInteger(); val peak = AtomicInteger()
        val transport = EchoBridgeTransport {
            val count = active.incrementAndGet(); peak.accumulateAndGet(count, ::maxOf)
            try { delay(10); JSONObject("""{"catalog":{"page":1,"pageSize":50,"total":0,"entries":[]}}""") }
            finally { active.decrementAndGet() }
        }
        List(6) { async { transport.tracks(endpoint, "", 1) } }.awaitAll()
        assertEquals(2, peak.get()); assertEquals(2, EchoBridgeTransport.ReadSlots.availablePermits)
    }
}
