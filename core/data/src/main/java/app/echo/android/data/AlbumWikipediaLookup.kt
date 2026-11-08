package app.echo.android.data

import app.echo.android.data.AlbumWikipediaMatch.claimEntityIds
import app.echo.android.data.AlbumWikipediaMatch.compact
import app.echo.android.data.AlbumWikipediaMatch.entityAllows
import app.echo.android.data.AlbumWikipediaMatch.entityId
import app.echo.android.data.AlbumWikipediaMatch.hasReleaseGroup
import app.echo.android.data.AlbumWikipediaMatch.pageIdentityCandidate
import app.echo.android.data.AlbumWikipediaMatch.pageMatches
import app.echo.android.data.AlbumWikipediaMatch.rankedPages
import app.echo.android.data.AlbumWikipediaMatch.searchQuery
import app.echo.android.data.AlbumWikipediaMatch.sitelink
import app.echo.android.data.AlbumWikipediaMatch.statementEntityId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

/**
 * MusicBrainz often has no Wikipedia URL even when an article describes the release.
 * A title search is only accepted when the article still matches this release.
 */
internal class AlbumWikipediaLookup(private val json: suspend (HttpUrl) -> JSONObject) {
    suspend fun find(
        title: String,
        alternateTitles: List<String>,
        artistNames: List<String>,
        year: Int?,
        labels: List<String>,
        releaseGroupId: String?,
        languages: List<String>,
    ): Pair<String, String>? {
        val names = artistNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val titles = (listOf(title) + alternateTitles).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (titles.isEmpty()) return null
        val langs = languages.distinct().filter { it in SUPPORTED }
        val groupId = releaseGroupId?.lowercase(Locale.ROOT)?.takeIf { it.matches(GROUP_ID) }
        if (groupId != null) sitelinkForReleaseGroup(groupId, langs)?.let { return it }
        if (names.none { compact(it).length >= 2 }) return null
        var failed = false
        var identityChecks = 0
        val queries = titles.flatMap { listOf(searchQuery(it, names), searchQuery(it, emptyList())) }.distinct().take(3)
        var requests = 0
        for (lang in langs) {
            for (query in queries) {
                if (requests++ >= 8) break
                val payload = try {
                    wikipedia(lang, mapOf(
                        "generator" to "search",
                        "gsrsearch" to query,
                        "gsrlimit" to "5",
                        "gsrnamespace" to "0",
                        "prop" to "extracts|pageprops",
                        "explaintext" to "1",
                        "exintro" to "1",
                        "exchars" to "1200",
                        "exlimit" to "5",
                        "redirects" to "1",
                    ))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed = true
                    break
                }
                for (page in rankedPages(payload)) {
                    val textMatches = pageMatches(page, titles, names, year, labels)
                    if (!textMatches && (!pageIdentityCandidate(page, titles, year, labels) || identityChecks >= 2)) continue
                    val entityId = entityId(page)
                    if (entityId != null) {
                        val entity = try {
                            wikidataEntity(entityId, langs)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                        // Translated artist names require positive P175 + label/alias identity evidence.
                        if (entity != null && !entityAllows(entity, groupId, year)) continue
                        if (!textMatches) {
                            identityChecks++
                            if (entity == null) { failed = true; continue }
                            val aliasesMatch = try { entityArtistsMatch(entity, names)
                            } catch (cancelled: CancellationException) { throw cancelled
                            } catch (_: Exception) { failed = true; false }
                            if (!aliasesMatch) continue
                        }
                    } else if (!textMatches) continue
                    page.text("title")?.let { return lang to it }
                }
            }
        }
        if (failed) throw IOException("Wikipedia search failed")
        return null
    }

    private suspend fun entityArtistsMatch(entity: JSONObject, names: List<String>): Boolean {
        val artists = claimEntityIds(entity, "P175").distinct().filter { it.matches(Regex("Q[0-9]+")) }.take(5)
        if (artists.size != names.size || artists.size > 4) return false
        val entities = wikidata(mapOf("action" to "wbgetentities", "ids" to artists.joinToString("|"),
            "props" to "labels|aliases", "languages" to "en|zh|ja")).optJSONObject("entities") ?: return false
        val aliases = artists.map { id ->
            val person = entities.optJSONObject(id) ?: return false
            val labels = person.optJSONObject("labels")
            val otherNames = person.optJSONObject("aliases")
            listOf("en", "zh", "ja").flatMap { lang ->
                listOfNotNull(labels?.optJSONObject(lang)?.text("value")) +
                    otherNames?.objects(lang).orEmpty().mapNotNull { it.text("value") }
            }.map(::compact).toSet()
        }
        val expected = names.map(::compact)
        return expected.all { name -> aliases.any { name in it } } && aliases.all { list -> expected.any { it in list } }
    }

    suspend fun pageIdentityAllows(page: JSONObject, releaseGroupId: String?, year: Int?, languages: List<String>): Boolean {
        val id = entityId(page) ?: return true
        return try { entityAllows(wikidataEntity(id, languages), releaseGroupId, year)
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { true }
    }

    private suspend fun sitelinkForReleaseGroup(groupId: String, languages: List<String>): Pair<String, String>? {
        val found = try {
            wikidata(mapOf("action" to "query", "list" to "search", "srlimit" to "1", "srsearch" to "haswbstatement:P436=$groupId"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }
        val entityId = statementEntityId(found) ?: return null
        val entity = try {
            wikidataEntity(entityId, languages)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        }
        if (!hasReleaseGroup(entity, groupId)) return null
        return sitelink(entity, languages)
    }

    private suspend fun wikidataEntity(id: String, languages: List<String>): JSONObject =
        wikidata(mapOf(
            "action" to "wbgetentities",
            "ids" to id,
            "props" to "sitelinks|claims",
            "sitefilter" to languages.joinToString("|") { "${it}wiki" },
        )).optJSONObject("entities")?.optJSONObject(id) ?: JSONObject()

    private suspend fun wikidata(parameters: Map<String, String>): JSONObject {
        val url = "https://www.wikidata.org/w/api.php".toHttpUrl().newBuilder().addQueryParameter("format", "json")
        parameters.forEach { (key, value) -> url.addQueryParameter(key, value) }
        return withTimeoutOrNull(4_000L) { json(url.build()) } ?: throw IOException("Wikidata lookup timed out")
    }

    private suspend fun wikipedia(language: String, parameters: Map<String, String>): JSONObject {
        val url = "https://$language.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query").addQueryParameter("format", "json").addQueryParameter("formatversion", "2")
        parameters.forEach { (key, value) -> url.addQueryParameter(key, value) }
        return withTimeoutOrNull(4_000L) { json(url.build()) } ?: throw IOException("Wikipedia search timed out")
    }

    private val GROUP_ID = Regex("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}")
    private val SUPPORTED = setOf("zh", "en", "ja")
}
