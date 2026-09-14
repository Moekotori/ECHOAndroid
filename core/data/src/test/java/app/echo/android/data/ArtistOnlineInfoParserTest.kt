package app.echo.android.data

import app.echo.android.model.library.ArtistOnlineQuery
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ArtistOnlineInfoParserTest {
    private val first = "11111111-1111-1111-1111-111111111111"
    private val second = "22222222-2222-2222-2222-222222222222"

    @Test fun matchesNativeAliasAndRejectsSimilarArtistNames() {
        val search = JSONObject("""{"artists":[
          {"id":"$first","name":"Mugendai Mewtype","aliases":[{"name":"夢限大みゅーたいぷ"}]},
          {"id":"$second","name":"夢限大みゅーたいぷ tribute"}
        ]}""")
        val candidates = ArtistOnlineInfoParser.candidates(search, ArtistOnlineQuery("夢限大みゅーたいぷ"))
        assertEquals(first, ArtistOnlineInfoParser.select(candidates, emptySet()))
    }

    @Test fun ambiguousNamesNeedUniqueAlbumEvidence() {
        val search = JSONObject("""{"artists":[{"id":"$first","name":"Air"},{"id":"$second","name":"AIR"}]}""")
        val candidates = ArtistOnlineInfoParser.candidates(search, ArtistOnlineQuery("Air"))
        assertNull(ArtistOnlineInfoParser.select(candidates, emptySet()))
        assertEquals(second, ArtistOnlineInfoParser.select(candidates, setOf(second)))
        assertNull(ArtistOnlineInfoParser.select(candidates, setOf(first, second)))
        assertTrue(ArtistOnlineInfoParser.candidates(search, ArtistOnlineQuery("   ")).isEmpty())
    }

    @Test fun albumEvidenceMustMatchTitleAndMemberRelationsMustPointIntoGroup() {
        val groups = JSONObject("""{"release-groups":[{"title":"Moon Safari","artist-credit":[{"artist":{"id":"$first"}}]}]}""")
        assertEquals(setOf(first), ArtistOnlineInfoParser.albumArtistIds(groups, "Moon Safari"))
        assertTrue(ArtistOnlineInfoParser.albumArtistIds(groups, "Different Album").isEmpty())
        val artist = JSONObject("""{"id":"$first","name":"Band","relations":[
          {"type":"member of band","direction":"backward","artist":{"name":"Current"}},
          {"type":"member of band","direction":"backward","ended":true,"artist":{"name":"Former"}},
          {"type":"member of band","direction":"forward","artist":{"name":"Other band"}}
        ]}""")
        assertEquals(listOf("Current"), ArtistOnlineInfoParser.profile(artist).members)
    }

    @Test fun searchTermsRemainQuoted() {
        assertEquals("\"A\\\" OR artist:* \\\\\"", ArtistOnlineInfoParser.quote("A\" OR artist:* \\"))
    }

    @Test fun wikiFallbackNeedsSharedIdentityAndRejectsConflictingMbid() {
        val artist = JSONObject("""{"id":"$first","relations":[{"url":{"resource":"https://twitter.com/BDP_yumemita"}}]}""")
        val wiki = JSONObject("""{"claims":{"P2002":[{"mainsnak":{"datavalue":{"value":"BDP_yumemita"}}}]}}""")
        assertTrue(ArtistOnlineInfoParser.matchesWikiEntity(artist, wiki))
        assertFalse(ArtistOnlineInfoParser.matchesWikiEntity(artist, JSONObject("""{"claims":{}}""")))
        wiki.getJSONObject("claims").put("P434", org.json.JSONArray("""[{"mainsnak":{"datavalue":{"value":"$second"}}}]"""))
        assertFalse(ArtistOnlineInfoParser.matchesWikiEntity(artist, wiki))
    }
}
