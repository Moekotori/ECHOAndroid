package app.echo.android.data

import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException
import java.text.Normalizer
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
        val queryTitle = titles.firstOrNull() ?: return null
        if (!canCorroborate(names, year, labels)) return null
        val langs = languages.distinct().filter { it in SUPPORTED }
        val groupId = releaseGroupId?.lowercase(Locale.ROOT)?.takeIf { it.matches(GROUP_ID) }
        if (groupId != null) sitelinkForReleaseGroup(groupId, langs)?.let { return it }
        var failed = false
        for (lang in langs) {
            val payload = try {
                wikipedia(lang, mapOf(
                    "generator" to "search",
                    "gsrsearch" to searchQuery(queryTitle, names),
                    "gsrlimit" to "5",
                    "gsrnamespace" to "0",
                    "prop" to "extracts|pageprops",
                    "explaintext" to "1",
                    "exintro" to "1",
                    "exchars" to "1500",
                    "exlimit" to "5",
                    "redirects" to "1",
                ))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
                continue
            }
            for (page in rankedPages(payload)) {
                if (!pageMatches(page, titles, names, year, labels)) continue
                val entityId = entityId(page)
                if (entityId != null) {
                    val entity = try {
                        wikidataEntity(entityId, langs)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        null
                    }
                    // A Wikidata outage must not hide a page the title, artist and year already corroborate.
                    if (entity != null && !entityAllows(entity, groupId, year)) continue
                }
                page.text("title")?.let { return lang to it }
            }
        }
        if (failed) throw IOException("Wikipedia search failed")
        return null
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
        return json(url.build())
    }

    private suspend fun wikipedia(language: String, parameters: Map<String, String>): JSONObject {
        val url = "https://$language.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query").addQueryParameter("format", "json").addQueryParameter("formatversion", "2")
        parameters.forEach { (key, value) -> url.addQueryParameter(key, value) }
        return json(url.build())
    }

    companion object {
        private val GROUP_ID = Regex("[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}")
        private val SUPPORTED = setOf("zh", "en", "ja")
        private val RELEASE_TYPES = setOf("Q482994", "Q134556", "Q169930")
        private val NON_RELEASE = setOf("Q5", "Q215380", "Q7366", "Q11424", "Q571", "Q4167410", "Q7889")

        fun wikiLanguage(languageTag: String): String =
            languageTag.substringBefore('-').lowercase(Locale.ROOT).takeIf { it in SUPPORTED } ?: "en"

        /** Chinese Wikipedia stores one article and converts it. Simplified UI must not show the raw traditional lead. */
        fun chineseVariant(languageTag: String): String? {
            val tag = languageTag.lowercase(Locale.ROOT).replace('_', '-')
            if (!tag.startsWith("zh")) return null
            return when {
                tag.contains("hk") || tag.contains("mo") -> "zh-hk"
                tag.contains("hant") || tag.contains("tw") -> "zh-tw"
                else -> "zh-cn"
            }
        }

        fun creditNames(release: JSONObject): List<String> = release.objects("artist-credit").mapNotNull {
            it.text("name") ?: it.optJSONObject("artist")?.text("name")
        }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        fun releaseYear(date: String?, albumYear: Int?): Int? =
            date?.take(4)?.toIntOrNull()?.takeIf { it in 1900..2100 } ?: albumYear?.takeIf { it in 1900..2100 }

        fun canCorroborate(artistNames: List<String>, year: Int?, labels: List<String>): Boolean {
            if (artistNames.none { phraseTokens(it).length >= 2 }) return false
            if (year != null) return true
            return labels.any { phraseTokens(it).length >= 2 }
        }

        fun searchQuery(title: String, artistNames: List<String>): String {
            fun phrase(value: String) = "\"${value.take(120).replace("\\", " ").replace("\"", " ")}\""
            val names = artistNames.map { it.trim() }.filter { phraseTokens(it).length >= 2 }.distinct().take(3)
            return (listOf(phrase(title)) + names.map(::phrase)).joinToString(" ")
        }

        fun statementEntityId(payload: JSONObject): String? = payload.optJSONObject("query")?.objects("search")
            ?.firstOrNull()?.text("title")?.takeIf { it.matches(Regex("Q[0-9]+")) }

        fun rankedPages(payload: JSONObject): List<JSONObject> = payload.optJSONObject("query")?.objects("pages").orEmpty()
            .filter { !it.has("missing") && it.optInt("ns", -1) == 0 && it.optJSONObject("pageprops")?.has("disambiguation") != true }
            .sortedBy { if (it.has("index")) it.optInt("index") else Int.MAX_VALUE }

        fun entityId(page: JSONObject): String? = page.optJSONObject("pageprops")?.text("wikibase_item")
            ?.takeIf { it.matches(Regex("Q[0-9]+")) }

        fun pageMatches(page: JSONObject, titles: List<String>, artistNames: List<String>, year: Int?, labels: List<String>): Boolean {
            val title = page.text("title") ?: return false
            val extract = page.text("extract") ?: return false
            return titles.any { wikipediaTitleMatches(title, it) } && wikipediaIntroCorroborates(extract, artistNames, year, labels)
        }

        fun wikipediaTitleMatches(pageTitle: String, albumTitle: String): Boolean {
            val album = compact(albumTitle)
            if (album.isEmpty()) return false
            val trimmed = pageTitle.trim()
            val cut = listOf(trimmed.indexOf(" ("), trimmed.indexOf("（")).filter { it >= 0 }.minOrNull()
            val base = if (cut == null) trimmed else trimmed.substring(0, cut).trim()
            if (compact(base) != album) return false
            val suffix = trimmed.removePrefix(base).trim()
            if (suffix.isEmpty()) return true
            val inner = suffix.drop(1).dropLast(1)
            val oneParenthetical = (suffix.startsWith("(") && suffix.endsWith(")")) || (suffix.startsWith("（") && suffix.endsWith("）"))
            return oneParenthetical && !inner.contains('(') && !inner.contains('（')
        }

        fun wikipediaIntroCorroborates(extract: String, artistNames: List<String>, year: Int?, labels: List<String>): Boolean {
            val names = artistNames.map { it.trim() }.filter { phraseTokens(it).length >= 2 }.distinct()
            if (names.isEmpty() || names.any { !mentionsPhrase(extract, it) }) return false
            if (year != null) return Regex("(?<!\\d)$year(?!\\d)").containsMatchIn(extract)
            return labels.any { phraseTokens(it).length >= 2 && mentionsPhrase(extract, it) }
        }

        /** P436 for another release group, a conflicting publication year, or a non-release entry all veto the page. */
        fun entityAllows(entity: JSONObject, releaseGroupId: String?, year: Int?): Boolean {
            val groupIds = claimStrings(entity, "P436").map { it.lowercase(Locale.ROOT) }
            if (releaseGroupId != null && releaseGroupId.lowercase(Locale.ROOT) in groupIds) return true
            if (groupIds.isNotEmpty()) return false
            val types = claimEntityIds(entity, "P31")
            if (types.any { it in NON_RELEASE } && types.none { it in RELEASE_TYPES }) return false
            if (year != null) {
                val years = claimYears(entity, "P577")
                if (years.isNotEmpty() && year !in years) return false
            }
            return true
        }

        fun hasReleaseGroup(entity: JSONObject, releaseGroupId: String): Boolean =
            claimStrings(entity, "P436").any { it.equals(releaseGroupId, ignoreCase = true) }

        fun sitelink(entity: JSONObject, languages: List<String>): Pair<String, String>? {
            val sites = entity.optJSONObject("sitelinks") ?: return null
            for (lang in languages) sites.optJSONObject("${lang}wiki")?.text("title")?.let { return lang to it }
            return null
        }

        private fun mentionsPhrase(text: String, phrase: String): Boolean {
            val needle = phraseTokens(phrase)
            if (needle.length < 2) return false
            return " ${phraseTokens(text)} ".contains(" $needle ")
        }

        private fun phraseTokens(value: String): String {
            val normalized = Normalizer.normalize(value, Normalizer.Form.NFKD).lowercase(Locale.ROOT)
            val out = StringBuilder()
            var previousAscii: Boolean? = null
            for (ch in normalized) {
                if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) continue
                if (!ch.isLetterOrDigit()) {
                    if (out.isNotEmpty() && out.last() != ' ') out.append(' ')
                    previousAscii = null
                    continue
                }
                val ascii = ch.code < 128
                if (previousAscii != null && previousAscii != ascii && out.last() != ' ') out.append(' ')
                out.append(ch)
                previousAscii = ascii
            }
            return out.toString().trim()
        }

        private fun compact(value: String): String = phraseTokens(value).replace(" ", "")

        private fun claimStrings(entity: JSONObject, property: String): List<String> = entity.optJSONObject("claims")
            ?.objects(property)?.mapNotNull { it.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.text("value") }.orEmpty()

        private fun claimEntityIds(entity: JSONObject, property: String): List<String> = entity.optJSONObject("claims")
            ?.objects(property)?.mapNotNull {
                it.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optJSONObject("value")?.text("id")
            }.orEmpty()

        private fun claimYears(entity: JSONObject, property: String): List<Int> = entity.optJSONObject("claims")
            ?.objects(property)?.mapNotNull {
                val time = it.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("time").orEmpty()
                Regex("([0-9]{4})").find(time)?.groupValues?.get(1)?.toIntOrNull()
            }.orEmpty()
    }
}
