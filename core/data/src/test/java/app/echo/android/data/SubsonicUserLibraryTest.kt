package app.echo.android.data

import app.echo.android.model.library.*
import java.net.URI
import java.net.URLDecoder
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SubsonicUserLibraryTest {
    private val endpoint = SubsonicEndpoint("https://music.example", "listener", "secret")
    private fun client(body: (String) -> String) = SubsonicClient(endpoint, httpGet = body)
    private fun ok(value: String) = """{"subsonic-response":{"status":"ok",$value}}"""
    private fun params(url: String, key: String) = URI(url).rawQuery.split('&').mapNotNull { part ->
        val split = part.split('=', limit = 2)
        if (split[0] == key) URLDecoder.decode(split.getOrElse(1) { "" }, "UTF-8") else null
    }

    @Test fun productionWritesUseFormPostAndKeepRepeatedSongIds() {
        val captured = AtomicReference<List<String>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/rest/updatePlaylist.view") { exchange ->
            captured.set(listOf(exchange.requestMethod, exchange.requestURI.rawQuery.orEmpty(), exchange.requestBody.bufferedReader().readText()))
            val response = """{"subsonic-response":{"status":"ok"}}""".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            SubsonicClient(SubsonicEndpoint("http://127.0.0.1:${server.address.port}", "listener", "secret"))
                .addPlaylistSongs("p1", listOf("a", "a", "b"))
            val request = captured.get()
            assertEquals("POST", request[0])
            assertEquals("", request[1])
            assertEquals(listOf("a", "a", "b"), params("https://example.invalid/?${request[2]}", "songIdToAdd"))
            assertFalse(request[2].contains("secret"))
        } finally { server.stop(0) }
    }

    @Test fun favoritePagesRetainTheirBoundariesAndServerIdentity() {
        val songs = (0..106).joinToString(",") { """{"id":"s$it","title":"Song $it"}""" }
        val page = client { ok(""""starred2":{"song":[$songs]}""") }
            .readUserLibrary(endpoint, NavidromeQuery.Collection(NavidromeCollection.Favorites, 1))
        assertEquals(50, page.entries.size)
        assertEquals("s50", page.entries.first().id)
        assertEquals("s99", page.entries.last().id)
        assertTrue(page.hasMore)
        assertTrue(page.entries.all { it.favorite && it.track?.source?.id == endpoint.sourceId })
    }

    @Test fun indexQueueRetainsDuplicateSongsAndExactCurrentOccurrence() {
        val urls = mutableListOf<String>()
        val client = client { url ->
            urls += url
            if (url.contains("getOpenSubsonicExtensions")) ok(""""openSubsonicExtensions":[{"name":"indexBasedQueue","versions":[1]}]""")
            else ok(""""playQueueByIndex":{"entry":[{"id":"a","title":"A"},{"id":"b","title":"B"},{"id":"a","title":"A"}],"currentIndex":2,"position":12345}""")
        }
        val queue = client.readQueue(endpoint)
        assertEquals(listOf("a", "b", "a"), queue.entries.map { it.id })
        assertEquals(2, queue.currentIndex)
        assertEquals(12345L, queue.positionMs)
        client.saveQueue(queue.entries.map { it.id }, queue.currentIndex, queue.positionMs)
        assertEquals(listOf("a", "b", "a"), params(urls.last(), "id"))
        assertEquals(listOf("2"), params(urls.last(), "currentIndex"))
    }

    @Test fun legacyQueueUsesSongIdentityAndMilliseconds() {
        val urls = mutableListOf<String>()
        val client = client { url -> urls += url
            if (url.contains("getOpenSubsonicExtensions")) error("unsupported") else ok(""""playQueue":{"entry":[{"id":"a"},{"id":"b"}],"current":"b","position":1200}""") }
        val queue = client.readQueue(endpoint)
        assertEquals(1, queue.currentIndex)
        client.saveQueue(listOf("a", "b"), 1, 1200)
        assertTrue(urls.last().contains("savePlayQueue.view"))
        assertEquals(listOf("b"), params(urls.last(), "current"))
        assertEquals(listOf("1200"), params(urls.last(), "position"))
    }

    @Test fun favoritesAndPlaylistMutationsUseTheirCorrectIdentityFields() {
        val urls = mutableListOf<String>()
        val client = client { url -> urls += url; """{"subsonic-response":{"status":"ok"}}""" }
        client.executeUserCommand(NavidromeCommand.Favorite(NavidromeKind.Artist, "artist1", true))
        client.executeUserCommand(NavidromeCommand.Favorite(NavidromeKind.Album, "album1", false))
        client.executeUserCommand(NavidromeCommand.Playlist("p", "A & B", "Notes", true, listOf("a", "b", "a")))
        assertEquals(listOf("artist1"), params(urls[0], "artistId"))
        assertEquals(listOf("album1"), params(urls[1], "albumId"))
        assertEquals(listOf("a", "b", "a"), params(urls[2], "songId"))
        assertEquals(listOf("Notes"), params(urls[3], "comment"))
        assertEquals(listOf("true"), params(urls[3], "public"))
    }

    @Test(expected = IllegalStateException::class) fun missingFavoriteContainerIsFailureNotAnEmptySnapshot() {
        client { """{"subsonic-response":{"status":"ok"}}""" }.favoriteEntries(endpoint)
    }

    @Test(expected = IllegalArgumentException::class) fun truncatedPlaylistCannotBecomeAnEmptyReplacement() {
        client { ok(""""playlist":{"id":"p","name":"Playlist","songCount":2,"entry":[{"id":"a"}]}""") }
            .playlistSettings(endpoint, "p")
    }

    @Test fun openSubsonicTagsReachAndroidAlbumGroupingAndGenre() {
        val song = JSONObject("""{"id":"a","artist":"Singer","displayAlbumArtist":"Band & Guests","albumArtists":[{"name":"Band"}],"genres":[{"name":"Jazz"}],"displayComposer":"Composer","album":"Album"}""").toSubsonicSong()
        val row = song.toLibraryTrackEntity(endpoint, 1)
        assertEquals("Band & Guests", row.albumArtist)
        assertEquals("Jazz", row.genre)
        assertEquals("Composer", row.composer)
        val structured = JSONObject("""{"id":"b","albumArtists":[{"name":"A"},{"name":"B"}]}""").toSubsonicSong()
        assertEquals("A & B", structured.albumArtist)
    }

    @Test fun translationsMergeAtEffectiveTimesWithoutConcatenatingWholeVersions() {
        val lyrics = parseSubsonicStructuredLyrics(JSONObject("""{"lyricsList":{"structuredLyrics":[
            {"lang":"ja","synced":true,"offset":100,"line":[{"start":1000,"value":"Original"}]},
            {"lang":"zh","synced":true,"offset":0,"line":[{"start":1100,"value":"Translation"}]},
            {"lang":"ja-Latn","synced":true,"offset":100,"line":[{"start":1000,"value":"Romaji"}]}
        ]}}"""))!!
        assertEquals(1, lyrics.lines.size)
        assertEquals(1100L, lyrics.lines.single().startMs)
        assertEquals("Original", lyrics.lines.single().text)
        assertEquals("Translation", lyrics.lines.single().translation)
        assertEquals("Romaji", lyrics.lines.single().romanization)
    }
}
