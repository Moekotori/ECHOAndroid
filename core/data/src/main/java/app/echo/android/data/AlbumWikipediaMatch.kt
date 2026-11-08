package app.echo.android.data

import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** Pure identity and text rules, kept separate from Wikipedia/Wikidata transport. */
internal object AlbumWikipediaMatch {
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
        val extract = page.text("extract") ?: return false
        return pageTitleAllows(page, titles) && wikipediaIntroCorroborates(extract, artistNames, year, labels)
    }

    fun pageIdentityCandidate(page: JSONObject, titles: List<String>, year: Int?, labels: List<String>): Boolean {
        val extract = page.text("extract") ?: return false
        return pageTitleAllows(page, titles) && releaseText(extract) && releaseEvidenceMatches(extract, year, labels)
    }

    private fun pageTitleAllows(page: JSONObject, titles: List<String>): Boolean {
        val title = page.text("title") ?: return false
        val extract = page.text("extract") ?: return false
        val sameTitle = titles.any { wikipediaTitleMatches(title, it) }
        // A translated page title is valid when its lead explicitly identifies the original album.
        val translatedTitle = titles.none { mentionsPhrase(title, it) } &&
            titles.any { mentionsPhrase(extract, it) } && releaseText(extract)
        return sameTitle || translatedTitle
    }

    fun wikipediaTitleMatches(pageTitle: String, albumTitle: String): Boolean {
        val album = compact(albumTitle)
        if (album.isEmpty()) return false
        val trimmed = pageTitle.trim()
        if (compact(trimmed) == album) return true
        val cut = listOf(trimmed.indexOf(" ("), trimmed.indexOf("（")).filter { it >= 0 }.minOrNull()
        val base = if (cut == null) trimmed else trimmed.substring(0, cut).trim()
        if (compact(base) != album) return false
        val suffix = trimmed.removePrefix(base).trim()
        if (suffix.isEmpty()) return true
        val inner = suffix.drop(1).dropLast(1)
        if (Regex("""\b(?:live|remix|soundtrack|acoustic|instrumental)\b|现场|現場|ライブ""", RegexOption.IGNORE_CASE).containsMatchIn(inner)) return false
        val oneParenthetical = (suffix.startsWith("(") && suffix.endsWith(")")) || (suffix.startsWith("（") && suffix.endsWith("）"))
        return oneParenthetical && !inner.contains('(') && !inner.contains('（')
    }

    fun wikipediaIntroCorroborates(extract: String, artistNames: List<String>, year: Int?, labels: List<String>): Boolean {
        val names = artistNames.map { it.trim() }.filter { phraseTokens(it).length >= 2 }.distinct()
        if (names.isEmpty() || names.any { !mentionsPhrase(extract, it) }) return false
        return releaseEvidenceMatches(extract, year, labels)
    }

    private fun releaseEvidenceMatches(extract: String, year: Int?, labels: List<String>): Boolean {
        if (year != null) {
            if (Regex("(?<!\\d)$year(?!\\d)").containsMatchIn(extract)) return true
            // A lead may omit the date; explicit conflicting years still veto it.
            if (Regex("(?<!\\d)(?:19|20)[0-9]{2}(?!\\d)").containsMatchIn(extract)) return false
            return releaseText(extract)
        }
        if (labels.isNotEmpty()) return labels.any { phraseTokens(it).length >= 2 && mentionsPhrase(extract, it) }
        return releaseText(extract)
    }

    private fun releaseText(extract: String): Boolean = Regex(
        """\b(?:album|single|ep|extended play|soundtrack|mixtape)\b|专辑|專輯|单曲|單曲|アルバム|シングル|음반|싱글""",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(extract.substringBefore('。').substringBefore('\n').take(500))

    /** P436 for another release group, a conflicting publication year, or a non-release entry all veto the page. */
    fun entityAllows(entity: JSONObject, releaseGroupId: String?, year: Int?): Boolean {
        val groupIds = claimStrings(entity, "P436").map { it.lowercase(Locale.ROOT) }
        if (releaseGroupId != null && releaseGroupId.lowercase(Locale.ROOT) in groupIds) return true
        if (releaseGroupId != null && groupIds.isNotEmpty()) return false
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
        // CJK prose has no spaces between a name and particles (周杰伦的 / 宇多田ヒカルの).
        // Keep token boundaries for Latin names, especially short names such as U2.
        val haystack = " ${phraseTokens(text)} "
        val start = if (needle.first().code < 128) "(?<![a-z0-9])" else ""
        val end = if (needle.last().code < 128) "(?![a-z0-9])" else ""
        return Regex(start + Regex.escape(needle) + end).containsMatchIn(haystack)
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

    fun compact(value: String): String = phraseTokens(value).replace(" ", "")

    private fun claimStrings(entity: JSONObject, property: String): List<String> = entity.optJSONObject("claims")
        ?.objects(property)?.mapNotNull { it.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.text("value") }.orEmpty()

    fun claimEntityIds(entity: JSONObject, property: String): List<String> = entity.optJSONObject("claims")
        ?.objects(property)?.mapNotNull {
            it.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optJSONObject("value")?.text("id")
        }.orEmpty()

    private fun claimYears(entity: JSONObject, property: String): List<Int> = entity.optJSONObject("claims")
        ?.objects(property)?.mapNotNull {
            val time = it.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("time").orEmpty()
            Regex("([0-9]{4})").find(time)?.groupValues?.get(1)?.toIntOrNull()
        }.orEmpty()
}
