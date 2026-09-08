package app.echo.android.data

import app.echo.android.model.library.AlbumSummary
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AlbumOnlineInfoParserTest {
    private val album = AlbumSummary("a", "Blue", "Artist A", "Artist A", null, 10, 1000, 2001)
    private fun candidate(artist: String = "Artist A", group: String = "group", count: Int = 10, id: String = "11111111-1111-1111-1111-111111111111") =
        JSONObject().put("id", id).put("title", "Blue").put("track-count", count)
            .put("release-group", JSONObject().put("id", group))
            .put("artist-credit", JSONArray().put(JSONObject().put("name", artist)))
    private fun search(vararg items: JSONObject) = JSONObject().put("releases", JSONArray(items.toList()))

    @Test fun rejectsSameTitleByAnotherArtist() {
        assertNull(AlbumOnlineInfoParser.selectRelease(search(candidate(artist = "Artist B")), album))
    }
    @Test fun doesNotTreatOneMemberOfJointCreditAsExactArtist() {
        val duet = candidate().put("artist-credit", JSONArray()
            .put(JSONObject().put("name", "Artist A").put("joinphrase", " & "))
            .put(JSONObject().put("name", "Artist B")))
        assertNull(AlbumOnlineInfoParser.selectRelease(search(duet), album))
    }
    @Test fun refusesAmbiguousReleaseGroups() {
        assertNull(AlbumOnlineInfoParser.selectRelease(search(candidate(), candidate(group = "another")), album))
    }
    @Test fun choosesMatchingTrackCountWithinSameGroup() {
        val expected = "22222222-2222-2222-2222-222222222222"
        assertEquals(expected, AlbumOnlineInfoParser.selectRelease(search(candidate(count = 12), candidate(id = expected)), album))
    }
    @Test fun refusesEmptyArtistAndNormalizesPunctuation() {
        assertNull(AlbumOnlineInfoParser.selectRelease(search(candidate()), album.copy(albumArtist = "", artist = "")))
        assertNotNull(AlbumOnlineInfoParser.selectRelease(search(candidate()), album.copy(title = "BLUE!")))
    }
    @Test fun queryEscapesQuotesAndBackslashes() {
        val query = AlbumOnlineInfoParser.query(album.copy(title = "A\" OR artist:* \\"))
        assertTrue(query.contains("A\\\" OR artist:* \\\\\""))
    }
    @Test fun parsesReleaseAndWorkCreditsWithoutLosingTrackContextInCache() {
        val payload = JSONObject("""{
          "id":"11111111-1111-1111-1111-111111111111", "title":"Blue", "date":"2001-03-04", "country":"JP",
          "artist-credit":[{"name":"Artist A"}],
          "label-info":[{"catalog-number":"TEST-001","label":{"name":"Example Records"}}],
          "relations":[{"type":"producer","artist":{"name":"Producer"}}],
          "media":[{"tracks":[{"title":"First Song", "recording":{"relations":[
             {"type":"performance","work":{"relations":[{"type":"composer","artist":{"name":"Composer"}}]}}
          ]}}]}]
        }""")
        val result = AlbumOnlineInfoParser.release(payload, 1234L).copy(partial = true,
            description = "An album introduction", wikipediaUrl = "https://en.wikipedia.org/wiki/Blue", wikipediaLanguage = "en")
        assertEquals(listOf("TEST-001"), result.catalogNumbers)
        assertEquals("First Song", result.credits.first { it.role == "composer" }.track)
        assertEquals(result, AlbumOnlineInfoParser.decode(AlbumOnlineInfoParser.encode(result)))
    }
}
