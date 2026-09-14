package app.echo.android.data

import app.echo.android.model.library.ArtistOnlineInfo
import app.echo.android.model.library.ArtistOnlineQuery
import org.json.JSONObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.Normalizer
import java.util.Locale

internal object ArtistOnlineInfoParser {
    private val mbid = Regex("[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}")
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")

    fun quote(value: String): String = "\"" + value.take(250).replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    fun candidates(payload: JSONObject, query: ArtistOnlineQuery): List<JSONObject> {
        val name = normalize(query.name)
        if (name.isEmpty()) return emptyList()
        return payload.objects("artists").filter { artist ->
            artist.text("id")?.matches(mbid) == true &&
                (listOfNotNull(artist.text("name"), artist.text("sort-name")) +
                    artist.objects("aliases").mapNotNull { it.text("name") }).any { normalize(it) == name }
        }.distinctBy { it.text("id") }
    }

    /** Album corroboration is used only for an ambiguous artist name. Never choose by search rank. */
    fun select(candidates: List<JSONObject>, albumArtistIds: Set<String>): String? =
        (if (candidates.size <= 1) candidates else candidates.filter { it.text("id") in albumArtistIds })
            .singleOrNull()?.text("id")

    fun albumArtistIds(payload: JSONObject, album: String): Set<String> = payload.objects("release-groups")
        .filter { normalize(it.optString("title")) == normalize(album) }
        .flatMap { it.objects("artist-credit") }
        .mapNotNull { it.optJSONObject("artist")?.text("id") }.toSet()

    /** An exact Wiki title alone cannot disambiguate musicians. Require a shared external identity. */
    fun matchesWikiEntity(artist: JSONObject, entity: JSONObject): Boolean {
        val claims = entity.optJSONObject("claims") ?: return false
        fun values(property: String) = claims.objects(property).mapNotNull {
            it.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.text("value")
        }
        val ids = values("P434")
        if (ids.isNotEmpty()) return artist.text("id") in ids
        val links = artist.objects("relations").mapNotNull { it.optJSONObject("url")?.text("resource")?.toHttpUrlOrNull() }
        return links.any { link ->
            when (link.host.removePrefix("www.")) {
                "twitter.com", "x.com" -> values("P2002").any { it.equals(link.pathSegments.firstOrNull(), ignoreCase = true) }
                "open.spotify.com" -> link.pathSegments.firstOrNull() == "artist" && link.pathSegments.getOrNull(1) in values("P1902")
                "music.apple.com" -> link.pathSegments.lastOrNull() in values("P2850")
                else -> values("P856").any { value ->
                    val official = value.toHttpUrlOrNull()
                    official?.host == link.host && official.encodedPath.trimEnd('/') == link.encodedPath.trimEnd('/')
                }
            }
        }
    }

    fun profile(json: JSONObject): ArtistOnlineInfo = ArtistOnlineInfo(
        musicBrainzId = json.getString("id"), name = json.getString("name"), kind = json.text("type"),
        area = json.optJSONObject("area")?.text("name") ?: json.text("country"),
        begin = json.optJSONObject("life-span")?.text("begin"), end = json.optJSONObject("life-span")?.text("end"),
        aliases = json.objects("aliases").mapNotNull { it.text("name") }.distinct().take(24),
        genres = json.objects("genres").sortedByDescending { it.optInt("count") }.mapNotNull { it.text("name") }.distinct().take(8),
        members = json.objects("relations").filter {
            it.text("type") == "member of band" && it.text("direction") == "backward" && !it.optBoolean("ended")
        }.mapNotNull { it.optJSONObject("artist")?.text("name") }.distinct().take(32),
        description = json.text("wikiExtract"), wikipediaUrl = json.text("wikiUrl"),
        wikipediaLanguage = json.text("wikiLanguage"), partial = json.optBoolean("partial"),
    )
}
