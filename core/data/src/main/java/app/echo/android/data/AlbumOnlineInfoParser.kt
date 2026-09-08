package app.echo.android.data

import app.echo.android.model.library.AlbumOnlineCredit
import app.echo.android.model.library.AlbumOnlineInfo
import app.echo.android.model.library.AlbumSummary
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

internal fun JSONObject.text(key: String): String? = optString(key).takeIf { it.isNotBlank() && it != "null" }
internal fun JSONObject.objects(key: String): List<JSONObject> = optJSONArray(key)?.let { array ->
    (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}.orEmpty()

internal object AlbumOnlineInfoParser {
    private fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    fun query(album: AlbumSummary): String {
        fun escaped(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
        return "release:\"${escaped(album.title)}\" AND artist:\"${escaped(album.albumArtist ?: album.artist.orEmpty())}\""
    }

    fun selectRelease(payload: JSONObject, album: AlbumSummary): String? {
        val artist = normalized(album.albumArtist?.takeIf { it.isNotBlank() } ?: album.artist.orEmpty())
        val title = normalized(album.title)
        if (artist.isEmpty() || title.isEmpty()) return null
        val candidates = payload.objects("releases").filter { release ->
            val credits = release.objects("artist-credit")
            val names = credits.flatMap { listOfNotNull(it.text("name"), it.optJSONObject("artist")?.text("name")) }
            val combined = credits.joinToString("") { (it.text("name") ?: it.optJSONObject("artist")?.text("name").orEmpty()) + it.optString("joinphrase") }
            normalized(release.optString("title")) == title &&
                ((credits.size == 1 && names.any { normalized(it) == artist }) || normalized(combined) == artist) &&
                release.text("id")?.matches(Regex("[a-fA-F0-9-]{36}")) == true
        }
        // Different release groups with the same title are ambiguous. Never pick by search score alone.
        val groups = candidates.map { it.optJSONObject("release-group")?.text("id") ?: it.optString("id") }.distinct()
        if (groups.size != 1) return null
        return candidates.maxByOrNull { release ->
            (if (release.optInt("track-count") == album.trackCount && album.trackCount > 0) 4 else 0) +
                (if (album.year != null && release.optString("date").startsWith(album.year.toString())) 2 else 0) +
                (if (release.optString("status") == "Official") 1 else 0)
        }?.text("id")
    }

    fun release(payload: JSONObject, now: Long): AlbumOnlineInfo {
        val credits = linkedSetOf<AlbumOnlineCredit>()
        fun addRelations(entity: JSONObject, track: String?) {
            entity.objects("relations").forEach { relation ->
                val name = relation.optJSONObject("artist")?.text("name") ?: return@forEach
                val role = relation.text("type") ?: return@forEach
                if (role in setOf("composer", "lyricist", "writer", "producer", "arranger", "instrument", "vocal", "performer", "mix", "recording", "engineer", "mastering")) {
                    val attributes = relation.optJSONArray("attributes")?.let { array ->
                        (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }.joinToString(", ")
                    }.orEmpty()
                    credits.add(AlbumOnlineCredit(if (attributes.isEmpty()) role else "$role · $attributes", name, track))
                }
            }
        }
        addRelations(payload, null)
        payload.objects("media").forEach { media -> media.objects("tracks").forEach { track ->
            val recording = track.optJSONObject("recording") ?: return@forEach
            val title = track.text("title") ?: recording.text("title")
            addRelations(recording, title)
            recording.objects("relations").forEach { relation ->
                relation.optJSONObject("work")?.let { addRelations(it, title) }
            }
        } }
        return AlbumOnlineInfo(
            releaseId = payload.getString("id"), releaseTitle = payload.getString("title"),
            artist = payload.objects("artist-credit").joinToString("") {
                (it.text("name") ?: it.optJSONObject("artist")?.text("name").orEmpty()) + it.optString("joinphrase")
            },
            date = payload.text("date"), country = payload.text("country"),
            labels = payload.objects("label-info").mapNotNull { it.optJSONObject("label")?.text("name") }.distinct(),
            catalogNumbers = payload.objects("label-info").mapNotNull { it.text("catalog-number") }.distinct(),
            credits = credits.take(160), description = null, wikipediaUrl = null, wikipediaLanguage = null,
            fetchedAtMs = now,
        )
    }

    fun encode(info: AlbumOnlineInfo): JSONObject = JSONObject().apply {
        put("releaseId", info.releaseId); put("title", info.releaseTitle); put("artist", info.artist)
        put("date", info.date); put("country", info.country)
        put("labels", JSONArray(info.labels)); put("catalogs", JSONArray(info.catalogNumbers))
        put("credits", JSONArray(info.credits.map { JSONObject().put("role", it.role).put("name", it.name).put("track", it.track) }))
        put("description", info.description); put("wikiUrl", info.wikipediaUrl); put("wikiLanguage", info.wikipediaLanguage)
        put("fetchedAt", info.fetchedAtMs); put("partial", info.partial)
    }

    fun decode(json: JSONObject): AlbumOnlineInfo {
        fun strings(key: String) = json.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        return AlbumOnlineInfo(json.getString("releaseId"), json.getString("title"), json.getString("artist"),
            json.text("date"), json.text("country"), strings("labels"), strings("catalogs"),
            json.objects("credits").map { AlbumOnlineCredit(it.getString("role"), it.getString("name"), it.text("track")) },
            json.text("description"), json.text("wikiUrl"), json.text("wikiLanguage"), json.getLong("fetchedAt"), json.optBoolean("partial"))
    }
}
