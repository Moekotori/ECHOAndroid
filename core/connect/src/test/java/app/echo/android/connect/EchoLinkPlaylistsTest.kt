package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteEndpoint
import java.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EchoLinkPlaylistsTest {
    private val endpoint = EchoRemoteEndpoint("pc", "PC", "127.0.0.1", 26789, "token")
    private val noCover = artworkPath("null")
    private val trackCover = artworkPath("\"track-cover\"")
    private val customCover = artworkPath("\"custom-cover\"")

    @Test
    fun skipsPagesWithoutLocalPlaylistsAndStopsAtTheUnfilteredEnd() = runBlocking {
        val requestedPages = mutableListOf<String?>()
        val transport = transport { url ->
            val page = url.queryParameter("page")
            requestedPages += page
            assertEquals("mix", url.queryParameter("q"))
            val items = when (page) {
                "1" -> """{"id":"netease","sourceLabel":"netease"},{"id":"unknown"}"""
                "2" -> """{"id":"local","sourceLabel":"local","artworkUrl":"$noCover"},
                    {"id":"custom","sourceProvider":"local","artworkUrl":"$customCover"}"""
                "3" -> """{"id":"remote","sourceProvider":"remote","sourceLabel":"local"}"""
                else -> error("Unexpected playlist page $page")
            }
            """{"totalCount":5,"playlists":[$items]}"""
        }

        val local = transport.fetchPlaylists(endpoint, "mix", page = 1, pageSize = 2)
        assertEquals(listOf("local", "custom"), local.playlists.map { it.id })
        assertNull(local.playlists.first().artworkUrl)
        assertEquals("http://127.0.0.1:26789$customCover", local.playlists.last().artworkUrl)
        assertEquals(3, local.nextPage)
        val end = transport.fetchPlaylists(endpoint, "mix", page = local.nextPage, pageSize = 2)
        assertTrue(end.playlists.isEmpty())
        assertEquals(0, end.nextPage)
        assertEquals(listOf("1", "2", "3"), requestedPages)
    }

    @Test
    fun loadsAndCachesOnlyASmallPreviewForMissingCovers() = runBlocking {
        var trackRequests = 0
        val transport = transport { url ->
            if (url.encodedPath.endsWith("/tracks")) {
                trackRequests++
                assertEquals("/echo-link/v1/library/playlists/local/tracks", url.encodedPath)
                assertEquals("1", url.queryParameter("page"))
                assertEquals("8", url.queryParameter("pageSize"))
                """{"totalCount":30,"tracks":[{"id":"blank","title":"No cover","artworkUrl":"$noCover"},
                    {"id":"track","title":"With cover","artworkUrl":"$trackCover"}]}"""
            } else {
                """{"playlists":[{"id":"local","trackCount":30,"sourceLabel":"local"},
                    {"id":"custom","sourceLabel":"local","artworkUrl":"$customCover"}]}"""
            }
        }
        val (playlist, custom) = transport.fetchPlaylists(endpoint, "", page = 1, pageSize = 500).playlists
        val artwork = EchoLinkPlaylistArtwork(transport)
        assertEquals("http://127.0.0.1:26789$customCover", artwork.resolve(endpoint, 1, custom) { null })
        assertEquals(0, trackRequests)
        repeat(2) {
            assertEquals("http://127.0.0.1:26789$trackCover", artwork.resolve(endpoint, 1, playlist) { null })
        }
        assertEquals(1, trackRequests)
    }

    private fun artworkPath(coverId: String): String {
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"coverId":$coverId}""".toByteArray())
        return "/echo-link/v1/artwork/s1.$payload.signature"
    }

    private fun transport(reply: (HttpUrl) -> String): OkHttpEchoLinkTransport =
        OkHttpEchoLinkTransport(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(reply(request.url).toResponseBody("application/json".toMediaType()))
                .build()
        }.build())
}
