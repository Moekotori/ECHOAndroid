package app.echo.android.data

import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubsonicWritebackTest {
    private val endpoint = SubsonicEndpoint("https://music.example", "listener", "secret")

    @Test fun createAndRenameEncodeNamesAndReturnServerIdentity() {
        val urls = mutableListOf<String>()
        val client = SubsonicClient(endpoint, httpGet = { url ->
            urls += url
            """{"subsonic-response":{"status":"ok","playlist":{"id":"remote-7"}}}"""
        })
        assertEquals("remote-7", client.createPlaylist("旅行 & Live"))
        client.renamePlaylist("remote-7", "A+B")
        assertEquals(listOf("旅行 & Live"), params(urls[0], "name"))
        assertEquals(listOf("remote-7"), params(urls[1], "playlistId"))
        assertEquals(listOf("A+B"), params(urls[1], "name"))
        assertFalse(urls.any { it.contains("secret") })
    }

    @Test fun removalUsesLiveServerPositionsIncludingRepeatedSongs() {
        val urls = mutableListOf<String>()
        val client = SubsonicClient(endpoint, httpGet = { url ->
            urls += url
            if (URI(url).path.endsWith("getPlaylist.view"))
                """{"subsonic-response":{"status":"ok","playlist":{"entry":[{"id":"hidden"},{"id":"target"},{"id":"other"},{"id":"target"}]}}}"""
            else """{"subsonic-response":{"status":"ok"}}"""
        })
        client.removePlaylistSong("p1", "target")
        assertEquals(2, urls.size)
        assertEquals(listOf("3", "1"), params(urls[1], "songIndexToRemove"))
        assertEquals(listOf("p1"), params(urls[1], "playlistId"))
    }

    @Test fun addRetainsRepeatedParametersAndStarsUseSongIdentity() {
        val urls = mutableListOf<String>()
        val client = SubsonicClient(endpoint, httpGet = { url ->
            urls += url; """{"subsonic-response":{"status":"ok"}}"""
        })
        client.addPlaylistSongs("p1", listOf("s1", "s2"))
        client.setStarred("s1", true)
        client.setStarred("s1", false)
        assertEquals(listOf("s1", "s2"), params(urls[0], "songIdToAdd"))
        assertTrue(URI(urls[1]).path.endsWith("/star.view"))
        assertTrue(URI(urls[2]).path.endsWith("/unstar.view"))
        assertEquals(listOf("s1"), params(urls[1], "id"))
    }

    @Test(expected = IllegalStateException::class)
    fun permissionFailureDoesNotCountAsAcceptedMutation() {
        SubsonicClient(endpoint, httpGet = {
            """{"subsonic-response":{"status":"failed","error":{"code":50,"message":"Permission denied"}}}"""
        }).renamePlaylist("p1", "new name")
    }

    private fun params(url: String, key: String) = URI(url).rawQuery.split('&').mapNotNull { part ->
        val pieces = part.split('=', limit = 2)
        if (pieces[0] == key) URLDecoder.decode(pieces.getOrElse(1) { "" }, "UTF-8") else null
    }
}
