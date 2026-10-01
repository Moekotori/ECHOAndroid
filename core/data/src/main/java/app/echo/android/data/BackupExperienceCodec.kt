package app.echo.android.data

import app.echo.android.model.backup.*
import app.echo.android.model.settings.*
import org.json.*

internal object BackupExperienceCodec {
    fun encodeSettings(json: JSONObject, value: EchoBackupSettings) {
        value.homeLayout?.let { json.put("homeLayout", JSONObject().put("order", JSONArray(it.order.map(EchoHomeSection::id))).put("hidden", JSONArray(it.hidden.map(EchoHomeSection::id)))) }
        json.putOpt("playerPageStyle", value.playerPageStyle).putOpt("playerTextScale", value.playerTextScale)
            .putOpt("playerArtworkScale", value.playerArtworkScale).putOpt("backgroundMode", value.backgroundMode)
            .putOpt("backgroundStyle", value.backgroundStyle).putOpt("backgroundBlur", value.backgroundBlur)
            .putOpt("backgroundBrightness", value.backgroundBrightness).putOpt("backgroundGlass", value.backgroundGlass).putOpt("backgroundScale", value.backgroundScale)
    }
    fun home(json: JSONObject?): EchoHomeLayout? {
        val value = json?.optJSONObject("homeLayout") ?: return null
        fun sections(name: String): List<EchoHomeSection> = value.optJSONArray(name)?.let { array ->
            (0 until array.length()).mapNotNull { EchoHomeSection.fromId(array.optString(it)) }
        }.orEmpty()
        return EchoHomeLayout(sections("order"), sections("hidden").toSet()).normalized()
    }
    fun encode(json: JSONObject, document: EchoBackupDocument) {
        json.put("history", JSONArray().apply { document.history.forEach { event -> put(JSONObject()
            .put("track", track(event.track)).put("listenedMs", event.listenedMs).put("playedAtEpochMs", event.playedAtEpochMs)
            .put("localEpochDay", event.localEpochDay).put("localHour", event.localHour).putOpt("source", event.source)) } })
        json.put("lyrics", JSONArray().apply { document.lyrics.forEach { item -> put(JSONObject().put("track", track(item.track))
            .put("userOffsetMs", item.userOffsetMs).putOpt("documentJson", item.documentJson)) } })
        json.put("assets", JSONArray().apply { document.assets.forEach { put(JSONObject().put("role", it.role).put("entry", it.entry)) } })
    }
    fun history(json: JSONObject): List<EchoBackupHistoryEvent> = objects(json, "history", 100000).map { value ->
        val listened = value.getLong("listenedMs"); val played = value.getLong("playedAtEpochMs"); val hour = value.getInt("localHour")
        require(listened >= 0 && played >= 0 && hour in 0..23)
        EchoBackupHistoryEvent(readTrack(value.getJSONObject("track")), listened, played, value.getLong("localEpochDay"), hour, value.optString("source").takeIf(String::isNotBlank))
    }
    fun lyrics(json: JSONObject): List<EchoBackupLyrics> = objects(json, "lyrics", 10000).map { value ->
        val text = if (value.has("documentJson")) value.getString("documentJson") else null
        require((text?.length ?: 0) <= 2 * 1024 * 1024)
        EchoBackupLyrics(readTrack(value.getJSONObject("track")), text, value.optLong("userOffsetMs").coerceIn(-30000,30000))
    }
    fun assets(json: JSONObject): List<EchoBackupAsset> = objects(json, "assets", 3).map { value ->
        val role = value.getString("role"); val entry = value.getString("entry")
        require(role in setOf("background","startup","font") && Regex("^assets/(background|startup|font)\\.[a-zA-Z0-9]{1,8}$").matches(entry))
        EchoBackupAsset(role, entry)
    }.also { require(it.map(EchoBackupAsset::role).distinct().size == it.size) }
    private fun objects(json: JSONObject, name: String, limit: Int): List<JSONObject> {
        val array = json.optJSONArray(name) ?: return emptyList()
        require(array.length() <= limit)
        return List(array.length()) { array.getJSONObject(it) }
    }
    private fun track(track: EchoBackupTrackRef) = JSONObject().put("title",track.title).put("artist",track.artist)
        .putOpt("album", track.album).putOpt("relativePath",track.relativePath).put("durationMs",track.durationMs)
    private fun readTrack(json: JSONObject) = EchoBackupTrackRef(json.optString("title"),json.optString("artist"),
        json.optString("relativePath").takeIf(String::isNotBlank), json.optLong("durationMs").coerceAtLeast(0), json.optString("album").takeIf(String::isNotBlank))
}
