package app.echo.android.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicJsonTest {
    @Test
    fun createsAFreshAuthenticationSaltForEveryRequest() {
        val requested = ArrayList<String>()
        var saltIndex = 0
        val client = SubsonicClient(
            endpoint = SubsonicEndpoint(
                baseUrl = "https://navidrome.example",
                username = "user",
                password = "pass",
            ),
            httpGet = { url ->
                requested += url
                """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
            },
            saltFactory = { "salt-${++saltIndex}" },
        )

        client.ping()
        client.ping()

        assertTrue(requested[0].contains("s=salt-1"))
        assertTrue(requested[1].contains("s=salt-2"))
        assertNotEquals(
            requested[0].substringAfter("t=").substringBefore('&'),
            requested[1].substringAfter("t=").substringBefore('&'),
        )
    }

    @Test
    fun readsJsonArrayOfAlbums() {
        val root = JSONObject(
            """
            {"album":[{"id":"1","name":"One"},{"id":"2","name":"Two"}]}
            """.trimIndent(),
        )
        val albums = root.jsonObjects("album")
        assertEquals(2, albums.size)
        assertEquals("1", albums[0].getString("id"))
        assertEquals("2", albums[1].getString("id"))
    }

    @Test
    fun readsSingleAlbumObject() {
        val root = JSONObject(
            """
            {"album":{"id":"only","name":"Solo"}}
            """.trimIndent(),
        )
        val albums = root.jsonObjects("album")
        assertEquals(1, albums.size)
        assertEquals("only", albums[0].getString("id"))
        assertEquals("Solo", albums[0].getString("name"))
    }

    @Test
    fun readsSingleSongObject() {
        val album = JSONObject(
            """
            {"song":{"id":"s1","title":"Alone"}}
            """.trimIndent(),
        )
        val songs = album.jsonObjects("song")
        assertEquals(1, songs.size)
        assertEquals("s1", songs[0].getString("id"))
    }

    @Test
    fun missingKeyReturnsEmpty() {
        assertEquals(0, JSONObject("{}").jsonObjects("album").size)
    }

    @Test
    fun readsSearch3SongArray() {
        val root = JSONObject(
            """
            {"searchResult3":{"song":[{"id":"s1","title":"One"},{"id":"s2","title":"Two"}]}}
            """.trimIndent(),
        )
        val songs = root.getJSONObject("searchResult3").jsonObjects("song")
        assertEquals(2, songs.size)
        assertEquals("s1", songs[0].getString("id"))
        assertEquals("Two", songs[1].getString("title"))
    }

    @Test
    fun search3ClientSkipsPerAlbumRequests() {
        val requested = ArrayList<String>()
        val client = SubsonicClient(
            endpoint = SubsonicEndpoint(
                baseUrl = "https://navidrome.example",
                username = "user",
                password = "pass",
            ),
            httpGet = { url ->
                requested += url.substringAfter("/rest/").substringBefore('?')
                when {
                    url.contains("search3.view") ->
                        """{"subsonic-response":{"status":"ok","version":"1.16.1","searchResult3":{"song":[{"id":"s1","title":"Bulk","artist":"A","album":"B","duration":12,"size":8}]}}}"""
                    else -> """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
                }
            },
        )
        val songs = client.fetchSongsBySearch3()
        assertEquals(1, songs.size)
        assertEquals("Bulk", songs[0].title)
        assertEquals("s1", songs[0].id)
        assertTrue(requested.contains("search3.view"))
        assertTrue(!requested.contains("getAlbum.view"))
    }
}
