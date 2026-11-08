package app.echo.android.data

import app.echo.android.model.library.AlbumOnlineInfo
import app.echo.android.model.library.AlbumSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.IOException

/** Bounded, independent source queries; neither source is a prerequisite for the other. */
internal class AlbumOnlineLookup(
    private val musicBrainz: suspend (String, Map<String, String>) -> JSONObject,
    private val json: suspend (HttpUrl) -> JSONObject,
) {
    private data class Outcome<T>(val value: T? = null, val failure: Exception? = null)
    private data class Article(val description: String, val url: String, val language: String)

    private suspend fun <T> attempt(budgetMs: Long, block: suspend () -> T?): Outcome<T> = try {
        withTimeoutOrNull(budgetMs) { Outcome(block()) } ?: Outcome(failure = IOException("Online album lookup timed out"))
    } catch (cancelled: CancellationException) { throw cancelled
    } catch (failure: Exception) { Outcome(failure = failure) }

    suspend fun fetch(album: AlbumSummary, language: String, variant: String?): AlbumOnlineInfo? = coroutineScope {
        val artist = AlbumOnlineQueryPolicy.artist(album) ?: return@coroutineScope null
        val query = album.copy(albumArtist = artist)
        val languages = AlbumOnlineQueryPolicy.wikipediaLanguages(language, album.title, artist)
        val startedAt = System.nanoTime()
        val independent = async {
            attempt(10_000L) {
                AlbumWikipediaLookup(json).find(
                    AlbumOnlineQueryPolicy.wikipediaTitles(album.title).first(), AlbumOnlineQueryPolicy.wikipediaTitles(album.title),
                    AlbumOnlineQueryPolicy.artistParts(artist), album.year, emptyList(), null, languages,
                )
            }
        }
        val catalogue = attempt(12_000L) { findRelease(query) }
        val initialPage = independent.await()
        val release = catalogue.value
        val matched = release != null
        var result = release?.let { AlbumOnlineInfoParser.release(it, System.currentTimeMillis()) }
            ?: AlbumOnlineInfo("", album.title, artist, null, null, emptyList(), emptyList(), emptyList(),
                null, null, null, System.currentTimeMillis())
        // Both initial sources and linked enrichment share an 18-second wall-clock budget.
        val remainingMs = (18_000L - (System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(1L)
        var supplementaryFailure: Exception? = null
        val enrichment = attempt(remainingMs) {
            val embeddedGroup = release?.optJSONObject("release-group")
            val groupId = embeddedGroup?.text("id")
            val group = if (initialPage.value == null && groupId?.matches(Regex("[a-fA-F0-9-]{36}")) == true) {
                attempt(3_000L) { musicBrainz("release-group/$groupId", mapOf("inc" to "url-rels")) }
                    .also { supplementaryFailure = it.failure }.value
            } else null
            val originalDate = group?.text("first-release-date") ?: embeddedGroup?.text("first-release-date")
            val year = AlbumWikipediaMatch.releaseYear(originalDate ?: result.date, album.year)
            val relations = group?.objects("relations").orEmpty() + release?.objects("relations").orEmpty()
            val linked = if (initialPage.value == null) attempt(3_000L) { resolveWikipedia(relations, languages.first()) }
                .also { supplementaryFailure = it.failure ?: supplementaryFailure }.value else null
            val page = initialPage.value ?: linked ?: if (matched) {
                AlbumWikipediaLookup(json).find(
                    AlbumOnlineQueryPolicy.wikipediaTitles(result.releaseTitle).first(), AlbumOnlineQueryPolicy.wikipediaTitles(album.title),
                    AlbumWikipediaMatch.creditNames(release).ifEmpty { AlbumOnlineQueryPolicy.artistParts(artist) },
                    year, result.labels, groupId, languages,
                )
            } else null
            page?.let { readArticle(it, variant, groupId, year, languages) }
        }
        val article = enrichment.value
        if (article != null) result = result.copy(description = article.description, wikipediaUrl = article.url, wikipediaLanguage = article.language)
        val failure = catalogue.failure ?: enrichment.failure ?: initialPage.failure ?: supplementaryFailure
        if (!matched && article == null) {
            failure?.let { throw it }
            return@coroutineScope null
        }
        result.copy(partial = catalogue.failure != null || (article == null && failure != null))
    }

    private suspend fun findRelease(album: AlbumSummary): JSONObject? {
        for (query in AlbumOnlineQueryPolicy.musicBrainzQueries(album)) {
            val search = musicBrainz("release/", mapOf("query" to query, "limit" to "100"))
            val id = AlbumOnlineInfoParser.selectRelease(search, album) ?: continue
            return musicBrainz("release/$id", mapOf("inc" to
                "artist-credits+labels+recordings+release-groups+url-rels+artist-rels+recording-level-rels+work-rels+work-level-rels"))
        }
        return null
    }

    private suspend fun readArticle(
        wikiPage: Pair<String, String>, variant: String?, groupId: String?, year: Int?, languages: List<String>,
    ): Article? {
        val (lang, title) = wikiPage
        val request = "https://$lang.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query").addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2").addQueryParameter("prop", "extracts|info|pageprops")
            .addQueryParameter("explaintext", "1").addQueryParameter("exintro", "1")
            .addQueryParameter("exchars", "1200").addQueryParameter("inprop", "url")
            .addQueryParameter("redirects", "1").addQueryParameter("titles", title)
        if (lang == "zh" && variant != null) request.addQueryParameter("variant", variant)
        val page = json(request.build()).optJSONObject("query")?.objects("pages")?.firstOrNull() ?: return null
        if (page.has("missing") || page.optJSONObject("pageprops")?.has("disambiguation") == true) return null
        if (!AlbumWikipediaLookup(json).pageIdentityAllows(page, groupId, year, languages)) return null
        val description = page.text("extract")?.take(1200) ?: return null
        val source = page.text("fullurl")?.toHttpUrlOrNull()?.takeIf { it.isHttps && it.host == "$lang.wikipedia.org" } ?: return null
        return Article(description, source.toString(), lang)
    }

    private suspend fun resolveWikipedia(relations: List<JSONObject>, language: String): Pair<String, String>? {
        val links = relations.mapNotNull { it.optJSONObject("url")?.text("resource")?.toHttpUrlOrNull() }
        val wikidata = links.firstOrNull { it.host == "www.wikidata.org" || it.host == "wikidata.org" }
        val entityId = wikidata?.pathSegments?.lastOrNull()?.takeIf { it.matches(Regex("Q[0-9]+")) }
        if (entityId != null) {
            val data = json("https://www.wikidata.org/w/api.php".toHttpUrl().newBuilder()
                .addQueryParameter("action", "wbgetentities").addQueryParameter("format", "json")
                .addQueryParameter("ids", entityId).addQueryParameter("props", "sitelinks")
                .addQueryParameter("sitefilter", "${language}wiki|enwiki").build())
            val sites = data.optJSONObject("entities")?.optJSONObject(entityId)?.optJSONObject("sitelinks")
            for (lang in listOf(language, "en").distinct()) {
                sites?.optJSONObject("${lang}wiki")?.text("title")?.let { return lang to it }
            }
        }
        for (lang in listOf(language, "en", "ja", "zh").distinct()) {
            val link = links.firstOrNull { it.host == "$lang.wikipedia.org" && it.encodedPath.startsWith("/wiki/") }
            if (link != null) return lang to link.pathSegments.drop(1).joinToString("/")
        }
        return null
    }

}
