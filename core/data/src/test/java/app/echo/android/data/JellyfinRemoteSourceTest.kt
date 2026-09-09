package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinRemoteSourceTest {
    @Test
    fun normalizesWebUiAndBareHosts() {
        assertEquals("http://nas:8096", normalizeJellyfinBaseUrl("http://nas:8096/web/index.html"))
        assertEquals("http://nas:8096", normalizeJellyfinBaseUrl("http://nas:8096/web"))
        assertEquals("http://nas:8096", normalizeJellyfinBaseUrl("nas:8096"))
        assertEquals("https://media.example.com", normalizeJellyfinBaseUrl("https://media.example.com/"))
    }

    @Test
    fun mapsItemsWithoutEmbeddingAccessTokens() {
        val endpoint = JellyfinEndpoint(
            baseUrl = "http://nas:8096/web",
            username = "user",
            password = "pass",
        )
        val item = JellyfinAudioItem(
            id = "abc",
            title = "Song",
            artist = "Artist",
            album = "Album",
            albumArtist = "Artist",
            year = 2024,
            trackNumber = 2,
            discNumber = 1,
            durationMs = 240_000,
            sizeBytes = 12_000,
            container = "flac",
            path = "Music/Album/song.flac",
            imageTag = "tag-1",
        )
        val entity = item.toLibraryTrackEntity(endpoint, scanRunId = 9L)
        assertEquals("http://nas:8096/Audio/abc/stream?static=true", entity.contentUri)
        assertTrue(entity.artworkUri.orEmpty().contains("/Items/abc/Images/Primary"))
        assertFalse(entity.contentUri.contains("api_key"))
        assertFalse(entity.artworkUri.orEmpty().contains("api_key"))
        assertTrue(entity.id.startsWith("jellyfin:"))
        assertEquals("audio/flac", entity.mimeType)
        assertEquals(endpoint.sourceId, entity.source)
    }

    @Test
    fun authenticateAndFetchUseMediaBrowserHeaders() {
        val requested = ArrayList<okhttp3.Request>()
        val client = JellyfinClient(
            endpoint = JellyfinEndpoint("http://nas:8096", "alice", "secret"),
            http = { request ->
                requested += request
                when {
                    request.url.encodedPath.endsWith("/Users/AuthenticateByName") ->
                        """{"AccessToken":"tok","User":{"Id":"u1"}}"""
                    else -> """{"Items":[{"Id":"i1","Name":"Song","Artists":["Artist"],"RunTimeTicks":10000000}],"TotalRecordCount":1}"""
                }
            },
        )
        val session = client.authenticate()
        assertEquals("tok", session.accessToken)
        assertEquals("u1", session.userId)
        val page = client.fetchAudioPage(session, 0)
        assertEquals(1, page.items.size)
        assertEquals("Song", page.items.single().title)
        assertEquals(1_000L, page.items.single().durationMs)
        assertTrue(requested.first().header("X-Emby-Authorization")!!.contains("ECHOAndroid"))
        assertEquals("tok", requested.last().header("X-Emby-Token"))
    }
}
