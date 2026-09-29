package app.echo.android.data

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class AlbumWikipediaLookupTest {
    private val group = "7a5e6b73-2174-4f0d-8ff0-f45d0133b988"
    private val zhIntro = "《Alea jacta est》（直译：骰子已掷下）是日本重金属乐团Ave Mujica的首张EP，于2023年9月13日由Bushiroad Music发行。"
    private val enIntro = "Alea Jacta Est is the debut extended play by Japanese metal band Ave Mujica. It was released on September 13, 2023, by Bushiroad Music."
    private val warcry = "Alea Jacta Est is the third studio album by the Asturian power metal band WarCry, released on January 1, 2004."

    @Test fun titleMatchAllowsParentheticalButNotADifferentWork() {
        assertTrue(AlbumWikipediaLookup.wikipediaTitleMatches("Alea jacta est", "Alea jacta est"))
        assertTrue(AlbumWikipediaLookup.wikipediaTitleMatches("Alea Jacta Est (EP)", "Alea jacta est"))
        assertTrue(AlbumWikipediaLookup.wikipediaTitleMatches("Alea jacta est（EP）", "ALEA jacta est"))
        assertFalse(AlbumWikipediaLookup.wikipediaTitleMatches("Alea iacta est", "Alea jacta est"))
        assertFalse(AlbumWikipediaLookup.wikipediaTitleMatches("Alea jacta est Live", "Alea jacta est"))
        assertFalse(AlbumWikipediaLookup.wikipediaTitleMatches("Alea jacta est (EP) (Remaster)", "Alea jacta est"))
    }

    @Test fun introRequiresArtistAndYearAndDoesNotMatchInsideOtherTokens() {
        assertTrue(AlbumWikipediaLookup.wikipediaIntroCorroborates(zhIntro, listOf("Ave Mujica"), 2023, emptyList()))
        assertTrue(AlbumWikipediaLookup.wikipediaIntroCorroborates(enIntro, listOf("Ave Mujica"), 2023, emptyList()))
        assertTrue(AlbumWikipediaLookup.wikipediaIntroCorroborates("Album by Björk in 1997.", listOf("Bjork"), 1997, emptyList()))
        assertFalse(AlbumWikipediaLookup.wikipediaIntroCorroborates(warcry, listOf("Ave Mujica"), 2023, emptyList()))
        assertFalse(AlbumWikipediaLookup.wikipediaIntroCorroborates(enIntro, listOf("Ave Mujica"), 2004, emptyList()))
        assertFalse(AlbumWikipediaLookup.wikipediaIntroCorroborates("released in 2023 by someone", listOf("U2"), 2023, emptyList()))
        assertTrue(AlbumWikipediaLookup.wikipediaIntroCorroborates("an album by U2 in 2023", listOf("U2"), 2023, emptyList()))
        assertTrue(AlbumWikipediaLookup.wikipediaIntroCorroborates(
            "Blue is an album by Artist A on Example Records.", listOf("Artist A"), null, listOf("Example Records")))
        assertFalse(AlbumWikipediaLookup.wikipediaIntroCorroborates(
            "Blue is an album by Artist A on Example Records.", listOf("Artist A"), null, listOf("Other Records")))
        assertFalse(AlbumWikipediaLookup.wikipediaIntroCorroborates(enIntro, listOf("Ave Mujica"), null, emptyList()))
    }

    @Test fun wikidataVetoesAnotherReleaseGroupABandPageOrTheWrongYear() {
        assertTrue(AlbumWikipediaLookup.entityAllows(entity("P31" to qid("Q482994"), "P577" to time(2023)), group, 2023))
        assertFalse(AlbumWikipediaLookup.entityAllows(entity("P31" to qid("Q482994"), "P577" to time(2004)), group, 2023))
        assertFalse(AlbumWikipediaLookup.entityAllows(entity("P31" to qid("Q215380")), group, 2023))
        assertFalse(AlbumWikipediaLookup.entityAllows(entity("P436" to stringClaim("other-group"), "P31" to qid("Q482994")), group, 2023))
        assertTrue(AlbumWikipediaLookup.entityAllows(entity("P436" to stringClaim(group), "P31" to qid("Q215380")), group, 2023))
        assertTrue(AlbumWikipediaLookup.hasReleaseGroup(entity("P436" to stringClaim(group.uppercase())), group))
        assertEquals("zh" to "Alea jacta est", AlbumWikipediaLookup.sitelink(JSONObject("""
            {"sitelinks":{"enwiki":{"title":"Alea Jacta Est (EP)"},"zhwiki":{"title":"Alea jacta est"}}}
        """), listOf("zh", "en")))
    }

    @Test fun searchRankingSkipsDisambiguationAndKeepsRelevanceOrder() {
        val pages = AlbumWikipediaLookup.rankedPages(JSONObject("""{"query":{"pages":[
            {"index":2,"ns":0,"title":"Later","extract":"later"},
            {"index":1,"ns":0,"title":"Dab","extract":"dab","pageprops":{"disambiguation":""}},
            {"index":4,"ns":0,"title":"Gone","missing":true},
            {"index":3,"ns":2,"title":"Talk"},
            {"title":"Unranked","ns":0,"extract":"last"}
        ]}}"""))
        assertEquals(listOf("Later", "Unranked"), pages.map { it.getString("title") })
        val page = JSONObject().put("title", "Alea Jacta Est (EP)").put("extract", enIntro)
        assertTrue(AlbumWikipediaLookup.pageMatches(page, listOf("Alea jacta est"), listOf("Ave Mujica"), 2023, emptyList()))
        assertFalse(AlbumWikipediaLookup.pageMatches(
            JSONObject().put("title", "Alea Jacta Est (album)").put("extract", warcry),
            listOf("Alea jacta est"), listOf("Ave Mujica"), 2023, emptyList()))
    }

    @Test fun chineseVariantFollowsTheInterfaceScript() {
        assertEquals("zh", AlbumWikipediaLookup.wikiLanguage("zh-CN"))
        assertEquals("zh-cn", AlbumWikipediaLookup.chineseVariant("zh-CN"))
        assertEquals("zh-cn", AlbumWikipediaLookup.chineseVariant("zh-Hans-CN"))
        assertEquals("zh-tw", AlbumWikipediaLookup.chineseVariant("zh-TW"))
        assertEquals("zh-tw", AlbumWikipediaLookup.chineseVariant("zh-Hant"))
        assertEquals("zh-hk", AlbumWikipediaLookup.chineseVariant("zh-HK"))
        assertNull(AlbumWikipediaLookup.chineseVariant("ja-JP"))
        assertEquals("en", AlbumWikipediaLookup.wikiLanguage("fr-FR"))
    }

    @Test fun parsesCreditsYearAndSearchQuery() {
        val release = JSONObject("""{"artist-credit":[{"name":"Ave Mujica"},{"name":"  "}]}""")
        assertEquals(listOf("Ave Mujica"), AlbumWikipediaLookup.creditNames(release))
        assertEquals(2023, AlbumWikipediaLookup.releaseYear("2023-09-13", 1999))
        assertEquals(2023, AlbumWikipediaLookup.releaseYear(null, 2023))
        assertNull(AlbumWikipediaLookup.releaseYear("1890-01-01", null))
        assertEquals("\"A  OR\" \"Ave Mujica\"", AlbumWikipediaLookup.searchQuery("A\" OR", listOf("Ave Mujica")))
        assertEquals("Q4714058", AlbumWikipediaLookup.statementEntityId(JSONObject("""{"query":{"search":[{"title":"Q4714058"}]}}""")))
        assertNull(AlbumWikipediaLookup.statementEntityId(JSONObject("""{"query":{"search":[{"title":"Alea Jacta Est"}]}}""")))
    }

    @Test fun corroboratedArticleIsUsedWhenMusicBrainzHasNoWikiLink() = runBlocking {
        val calls = mutableListOf<String>()
        val found = lookup(calls) { url ->
            when {
                url.queryParameter("list") == "search" -> JSONObject("""{"query":{"search":[]}}""")
                url.host == "zh.wikipedia.org" -> page("Alea jacta est", zhIntro, "Q135005838")
                url.queryParameter("ids") == "Q135005838" -> wrapped("Q135005838", entity("P31" to qid("Q482994"), "P577" to time(2023)))
                else -> error("unexpected $url")
            }
        }.find("Alea jacta est", emptyList(), listOf("Ave Mujica"), 2023, listOf("BUSHIROAD MUSIC"), group, listOf("zh", "en"))
        assertEquals("zh" to "Alea jacta est", found)
        assertTrue(calls.none { it.contains("en.wikipedia.org") })
    }

    @Test fun releaseGroupStatementSkipsTitleSearch() = runBlocking {
        val calls = mutableListOf<String>()
        val found = lookup(calls) { url ->
            when {
                url.queryParameter("list") == "search" -> JSONObject("""{"query":{"search":[{"title":"Q135005838"}]}}""")
                url.queryParameter("ids") == "Q135005838" -> wrapped("Q135005838",
                    entity("P436" to stringClaim(group)).apply {
                        put("sitelinks", JSONObject().put("zhwiki", JSONObject().put("title", "Alea jacta est")))
                    })
                else -> error("unexpected $url")
            }
        }.find("Alea jacta est", emptyList(), listOf("Ave Mujica"), 2023, emptyList(), group, listOf("zh", "en"))
        assertEquals("zh" to "Alea jacta est", found)
        assertTrue(calls.none { it.contains("wikipedia.org") })
    }

    @Test fun bandPageLosesToTheReleasePage() = runBlocking {
        val found = lookup { url ->
            when {
                url.queryParameter("list") == "search" -> JSONObject("""{"query":{"search":[]}}""")
                url.host == "zh.wikipedia.org" -> JSONObject("""{"query":{"pages":[
                    {"index":1,"ns":0,"title":"Ave Mujica","extract":"Ave Mujica is a band formed in 2023.","pageprops":{"wikibase_item":"Q1"}},
                    {"index":2,"ns":0,"title":"Ave Mujica (EP)","extract":"Ave Mujica is the debut EP by Ave Mujica, released in 2023.","pageprops":{"wikibase_item":"Q2"}}
                ]}}""")
                url.queryParameter("ids") == "Q1" -> wrapped("Q1", entity("P31" to qid("Q215380")))
                url.queryParameter("ids") == "Q2" -> wrapped("Q2", entity("P31" to qid("Q482994"), "P577" to time(2023)))
                else -> error("unexpected $url")
            }
        }.find("Ave Mujica", emptyList(), listOf("Ave Mujica"), 2023, emptyList(), group, listOf("zh"))
        assertEquals("zh" to "Ave Mujica (EP)", found)
    }

    @Test fun oneLanguageOutageStillUsesAnother() = runBlocking {
        val found = lookup { url ->
            when (url.host) {
                "zh.wikipedia.org" -> throw IOException("down")
                "en.wikipedia.org" -> page("Alea Jacta Est (EP)", enIntro, null)
                else -> error("unexpected $url")
            }
        }.find("Alea jacta est", emptyList(), listOf("Ave Mujica"), 2023, emptyList(), null, listOf("zh", "en"))
        assertEquals("en" to "Alea Jacta Est (EP)", found)
    }

    @Test fun totalSearchFailureStaysPartial() = runBlocking {
        try {
            lookup { throw IOException("down") }.find(
                "Alea jacta est", emptyList(), listOf("Ave Mujica"), 2023, emptyList(), null, listOf("zh", "en"))
            fail()
        } catch (failure: IOException) {
            assertEquals("Wikipedia search failed", failure.message)
        }
    }

    private fun lookup(calls: MutableList<String> = mutableListOf(), answer: (HttpUrl) -> JSONObject) =
        AlbumWikipediaLookup { url -> calls += url.toString(); answer(url) }

    private fun page(title: String, extract: String, item: String?): JSONObject {
        val props = if (item == null) JSONObject() else JSONObject().put("wikibase_item", item)
        return JSONObject().put("query", JSONObject().put("pages", org.json.JSONArray().put(
            JSONObject().put("index", 1).put("ns", 0).put("title", title).put("extract", extract).put("pageprops", props))))
    }

    private fun wrapped(id: String, entity: JSONObject) = JSONObject().put("entities", JSONObject().put(id, entity))

    private fun entity(vararg claims: Pair<String, JSONObject>): JSONObject {
        val all = JSONObject()
        claims.forEach { (property, claim) -> all.put(property, org.json.JSONArray().put(claim)) }
        return JSONObject().put("claims", all)
    }

    private fun qid(id: String) = JSONObject().put("mainsnak", JSONObject().put("datavalue",
        JSONObject().put("value", JSONObject().put("id", id))))

    private fun stringClaim(value: String) = JSONObject().put("mainsnak", JSONObject().put("datavalue",
        JSONObject().put("value", value)))

    private fun time(year: Int) = JSONObject().put("mainsnak", JSONObject().put("datavalue",
        JSONObject().put("value", JSONObject().put("time", "+$year-00-00T00:00:00Z"))))
}
