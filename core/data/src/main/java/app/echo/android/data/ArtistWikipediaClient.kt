package app.echo.android.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

internal class ArtistWikipediaClient(private val transport: ArtistOnlineTransport) {
    suspend fun enrich(artist: JSONObject, language: String) {
        val links = artist.objects("relations").mapNotNull { it.optJSONObject("url")?.text("resource")?.toHttpUrlOrNull() }
        val languages = listOf(language, "en", "ja", "zh", "ko").distinct()
        val titles = linkedMapOf<String, String>()
        links.forEach { link ->
            languages.firstOrNull { link.host == "$it.wikipedia.org" && link.encodedPath.startsWith("/wiki/") }
                ?.let { titles[it] = link.pathSegments.drop(1).joinToString("/") }
        }
        val linkedEntity = links.firstOrNull { it.host in setOf("www.wikidata.org", "wikidata.org") }
            ?.pathSegments?.lastOrNull()?.takeIf { it.matches(Regex("Q[0-9]+")) }
        if (linkedEntity != null) addSitelinks(entity(linkedEntity, languages), languages, titles)
        if (titles.isEmpty()) {
            // MusicBrainz sometimes lacks Wiki relations even when both services describe the artist.
            val nativeLanguage = if (artist.getString("name").any { it in '\u3040'..'\u30ff' }) "ja" else language
            val names = (listOf(artist.getString("name")) + artist.objects("aliases").mapNotNull { it.text("name") }).distinct().take(3)
            for (lang in (listOf(nativeLanguage, language, "en") + languages).distinct().take(3)) {
                val pages = pages(lang, names)
                var matched = false
                for (page in pages.filter(::validPage)) {
                    val id = page.optJSONObject("pageprops")?.text("wikibase_item")?.takeIf { it.matches(Regex("Q[0-9]+")) } ?: continue
                    val data = entity(id, languages)
                    if (!ArtistOnlineInfoParser.matchesWikiEntity(artist, data)) continue
                    titles[lang] = page.getString("title")
                    addSitelinks(data, languages, titles)
                    matched = true
                    break
                }
                if (matched) break
            }
        }
        val lang = languages.firstOrNull { titles.containsKey(it) } ?: return
        val page = pages(lang, listOf(titles.getValue(lang))).firstOrNull(::validPage) ?: return
        val link = page.text("fullurl")?.toHttpUrlOrNull()?.takeIf { it.isHttps && it.host == "$lang.wikipedia.org" } ?: return
        artist.put("wikiExtract", page.text("extract")?.take(5000)).put("wikiUrl", link.toString()).put("wikiLanguage", lang)
    }

    private fun validPage(page: JSONObject): Boolean = !page.has("missing") &&
        page.optInt("ns", -1) == 0 && page.optJSONObject("pageprops")?.has("disambiguation") != true

    private fun addSitelinks(entity: JSONObject, languages: List<String>, titles: MutableMap<String, String>) {
        val sites = entity.optJSONObject("sitelinks")
        languages.forEach { lang -> sites?.optJSONObject("${lang}wiki")?.text("title")?.let { titles[lang] = it } }
    }

    private suspend fun entity(id: String, languages: List<String>): JSONObject = json("https://www.wikidata.org/w/api.php",
        mapOf("action" to "wbgetentities", "format" to "json", "ids" to id, "props" to "sitelinks|claims",
            "sitefilter" to languages.joinToString("|") { "${it}wiki" }))
        .optJSONObject("entities")?.optJSONObject(id) ?: JSONObject()

    private suspend fun pages(language: String, names: List<String>): List<JSONObject> =
        json("https://$language.wikipedia.org/w/api.php", mapOf("action" to "query", "format" to "json", "formatversion" to "2",
            "prop" to "extracts|info|pageprops", "explaintext" to "1", "exintro" to "1", "exchars" to "5000",
            "exlimit" to "3", "inprop" to "url", "redirects" to "1", "titles" to names.joinToString("|") { it.replace('|', ' ') }))
            .optJSONObject("query")?.objects("pages").orEmpty()

    private suspend fun json(base: String, parameters: Map<String, String>): JSONObject = transport.json(base.toHttpUrl().newBuilder().apply {
        parameters.forEach { (key, value) -> addQueryParameter(key, value) }
    }.build())
}
