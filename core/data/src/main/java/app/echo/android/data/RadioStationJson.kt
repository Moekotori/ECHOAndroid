package app.echo.android.data

import app.echo.android.model.radio.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

internal object RadioStationJson {
    fun validate(station: EchoRadioStation) {
        require(station.id.isNotBlank() && station.id.length <= 200 && station.name.isNotBlank() && station.name.length <= 120)
        require(EchoRadioStation.validUrl(station.url))
        require(listOf(station.artworkUrl, station.homepage, station.videoUrl).all { it == null || EchoRadioStation.validUrl(it) })
        require(station.description.length <= 32768 && station.genre.length <= 512 && station.language.length <= 128)
        require(station.streams.size <= 15 && station.streams.all { EchoRadioStation.validUrl(it.url) })
        require(station.defaultStreamUrl == null || station.defaultStreamUrl == station.url || station.streams.any { it.url == station.defaultStreamUrl })
    }
    fun encode(station: EchoRadioStation): JSONObject = JSONObject().put("id", station.id).put("name", station.name).put("url", station.url)
        .put("artwork", station.artworkUrl).put("coverUrl", station.artworkUrl).put("favorite", station.favorite)
        .put("lastPlayed", station.lastPlayedMs).put("lastPlayedAt", station.lastPlayedMs.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).toString() })
        .put("createdAt", Instant.now().toString()).put("updatedAt", Instant.now().toString())
        .put("description", station.description).put("genre", station.genre).put("homepage", station.homepage)
        .put("codec", station.codec).put("bitrate", station.bitrateKbps).put("countryCode", station.countryCode)
        .put("language", station.language).put("videoUrl", station.videoUrl).put("defaultStreamUrl", station.defaultStreamUrl)
        .put("streams", JSONArray().apply { station.streams.forEach { put(JSONObject().put("url", it.url).put("codec", it.codec).put("bitrate", it.bitrateKbps)) } })

    fun decode(item: JSONObject): EchoRadioStation {
        val streams = item.optJSONArray("streams") ?: JSONArray()
        require(streams.length() <= 15)
        val played = item.optLong("lastPlayed", 0).takeIf { it > 0 } ?: try { Instant.parse(item.optString("lastPlayedAt")).toEpochMilli() } catch (_: Exception) { 0 }
        return EchoRadioStation(item.optString("id").ifBlank { UUID.randomUUID().toString() }, item.getString("name").trim().take(120), item.getString("url").trim(),
            RadioArtworkPolicy.corrected(item.optional("coverUrl") ?: item.optional("artwork")), item.optBoolean("favorite"), played,
            item.optString("description").take(32768), item.optString("genre").take(512), item.optional("homepage"), item.optional("codec"),
            item.optInt("bitrate").takeIf { it > 0 }, item.optString("countryCode").take(2), item.optString("language").take(128),
            List(streams.length()) { i -> streams.getJSONObject(i).let { EchoRadioStream(it.getString("url"), it.optional("codec"), it.optInt("bitrate").takeIf { rate -> rate > 0 }) } },
            item.optional("defaultStreamUrl"), item.optional("videoUrl")).also(::validate)
    }
    private fun JSONObject.optional(key: String) = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
