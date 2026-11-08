package app.echo.android.data

import app.echo.android.model.library.AlbumSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AlbumOnlineLookupTest {
    private val album = AlbumSummary("a", "Blue", "Artist A", "Artist A", null, 10, 1000, 2001)
    private val id = "11111111-1111-1111-1111-111111111111"
    private val group = "22222222-2222-2222-2222-222222222222"

    private fun wiki(url: HttpUrl): JSONObject = if (url.queryParameter("generator") == "search") {
        JSONObject().put("query", JSONObject().put("pages", JSONArray().put(JSONObject()
            .put("ns", 0).put("title", "Blue (album)").put("extract", "Blue is an album by Artist A, released in 2001."))))
    } else {
        JSONObject().put("query", JSONObject().put("pages", JSONArray().put(JSONObject()
            .put("title", "Blue (album)").put("extract", "Blue is an album by Artist A, released in 2001.")
            .put("fullurl", "https://en.wikipedia.org/wiki/Blue_(album)"))))
    }

    private fun release() = JSONObject().put("id", id).put("title", "Blue").put("date", "2020-01-01")
        .put("artist-credit", JSONArray().put(JSONObject().put("name", "Artist A")))
        .put("release-group", JSONObject().put("id", group).put("first-release-date", "2001-02-03"))

    @Test fun noCatalogueMatchStillLooksUpAnIndependentArticle() = runBlocking {
        val lookup = AlbumOnlineLookup({ _, _ -> JSONObject().put("releases", JSONArray()) }, ::wiki)
        val result = lookup.fetch(album, "en", null)!!
        assertEquals("", result.releaseId)
        assertEquals("https://en.wikipedia.org/wiki/Blue_(album)", result.wikipediaUrl)
        assertNull(result.date) // Do not manufacture release facts from the local tags.
    }

    @Test fun catalogueOutageStillReturnsTheArticleAndMarksPartial() = runBlocking {
        val result = AlbumOnlineLookup({ _, _ -> throw IOException("catalogue down") }, ::wiki)
            .fetch(album, "en", null)!!
        assertNotNull(result.description)
        assertTrue(result.partial)
    }

    @Test fun encyclopediaStartsWhileTheCatalogueIsStillWaiting() = runBlocking {
        val wikiStarted = CompletableDeferred<Unit>()
        val result = withTimeout(1_000L) {
            AlbumOnlineLookup({ _, _ -> wikiStarted.await(); JSONObject() }, { url ->
                wikiStarted.complete(Unit)
                wiki(url)
            }).fetch(album, "en", null)
        }
        assertNotNull(result?.description)
    }

    @Test fun independentlyFoundPageIsRejectedWhenItsGroupConflictsWithTheCatalogue() = runBlocking {
        val lookup = AlbumOnlineLookup({ path, _ ->
            if (path == "release/") JSONObject().put("releases", JSONArray().put(release())) else release()
        }, { url ->
            if (url.host == "www.wikidata.org") JSONObject("""{"entities":{"Q123":{"claims":{"P436":[{"mainsnak":{"datavalue":{"value":"different-group"}}}]}}}}""")
            else wiki(url).also { payload ->
                if (url.queryParameter("titles") != null) payload.getJSONObject("query").getJSONArray("pages")
                    .getJSONObject(0).put("pageprops", JSONObject().put("wikibase_item", "Q123"))
            }
        })
        val result = lookup.fetch(album, "en", null)!!
        assertEquals(id, result.releaseId)
        assertNull(result.description)
    }

    @Test fun sourceFailureDoesNotBecomeACacheableNoMatch() = runBlocking {
        try {
            AlbumOnlineLookup({ _, _ -> throw IOException("catalogue down") }, { JSONObject() })
                .fetch(album, "en", null)
            fail("Failure must not become a negative cache entry")
        } catch (failure: IOException) {
            assertEquals("catalogue down", failure.message)
        }
    }

    @Test fun reissueUsesOriginalYearEvenWhenGroupDetailsFail() = runBlocking {
        val lookup = AlbumOnlineLookup({ path, _ -> when (path) {
            "release/" -> JSONObject().put("releases", JSONArray().put(release()))
            "release/$id" -> release()
            else -> throw IOException("group down")
        } }, { url -> if (url.host == "www.wikidata.org") JSONObject() else wiki(url) })
        val result = lookup.fetch(album.copy(year = 2020), "en", null)!!
        assertNotNull(result.description)
        assertEquals("2020-01-01", result.date)
        assertEquals(id, result.releaseId)
    }

    @Test fun strippedEditionGetsOneAdditionalCatalogueSearch() = runBlocking {
        val queries = mutableListOf<String>()
        val lookup = AlbumOnlineLookup({ path, params -> when (path) {
            "release/" -> {
                queries += params.getValue("query")
                assertEquals("100", params["limit"])
                JSONObject().put("releases", if (queries.size == 1) JSONArray() else JSONArray().put(release()))
            }
            "release/$id" -> release()
            else -> JSONObject()
        } }, { url -> if (url.host == "www.wikidata.org") JSONObject() else wiki(url) })
        val result = lookup.fetch(album.copy(title = "Blue (Deluxe Edition)"), "en", null)!!
        assertEquals(id, result.releaseId)
        assertEquals(2, queries.size)
        assertTrue(queries.last().contains("release:\"Blue\""))
    }

    @Test fun callerCancellationStopsBeforeAnyFallbackRequest() = runBlocking {
        try {
            AlbumOnlineLookup({ _, _ -> throw CancellationException("left page") }, { error("must not run") })
                .fetch(album, "en", null)
            fail("Must preserve cancellation")
        } catch (cancelled: CancellationException) {
            assertEquals("left page", cancelled.message)
        }
    }
}
