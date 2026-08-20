package app.echo.android.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SubsonicJsonTest {
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
}
