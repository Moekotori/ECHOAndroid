package app.echo.android.connect

import app.echo.android.model.connect.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object EchoPersonalStateCodec {
    fun encode(state: EchoPersonalState): JSONObject {
        require(state.history.size <= EchoPersonalStateLimits.History && state.bookmarks.size <= EchoPersonalStateLimits.Bookmarks && state.lyricsOffsets.size <= EchoPersonalStateLimits.Offsets)
        fun EchoSyncTrackRef.json() = JSONObject().put("title", title).put("artist", artist)
            .put("album", album ?: JSONObject.NULL).put("durationMs", durationMs)
        return JSONObject().put("version", 1).put("revision", state.revision)
            .put("history", JSONArray().apply { state.history.forEach { event ->
                put(JSONObject().put("track", event.track.json()).put("listenedMs", event.listenedMs)
                    .put("playedAtEpochMs", event.playedAtEpochMs).put("localEpochDay", event.localEpochDay).put("localHour", event.localHour))
            } }).put("bookmarks", JSONArray().apply { state.bookmarks.forEach {
                put(JSONObject().put("track", it.track.json()).put("positionMs", it.positionMs).put("label", it.label).put("endPositionMs", it.endPositionMs ?: JSONObject.NULL))
            } }).put("lyricsOffsets", JSONArray().apply { state.lyricsOffsets.forEach {
                put(JSONObject().put("track", it.track.json()).put("offsetMs", it.offsetMs))
            } }).put("resume", state.resume?.let { JSONObject().put("track", it.track.json()).put("positionMs", it.positionMs) } ?: JSONObject.NULL)
    }

    fun revision(state: EchoPersonalState): String = MessageDigest.getInstance("SHA-256")
        .digest(encode(state.copy(revision = "", resume = null)).toString().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun decode(json: JSONObject): EchoPersonalState {
        require(json.getInt("version") == 1)
        fun JSONObject.track(): EchoSyncTrackRef {
            val title = getString("title"); val artist = getString("artist")
            val album = if (isNull("album")) null else optString("album")
            val duration = getLong("durationMs")
            require(title.length in 1..500 && artist.length <= 500 && (album?.length ?: 0) <= 500 && duration in 0..EchoPersonalStateLimits.MaxDurationMs)
            return EchoSyncTrackRef(title, artist, album, duration)
        }
        fun JSONObject.position(): Long = getLong("positionMs").also { require(it in 0..EchoPersonalStateLimits.MaxDurationMs) }
        fun <T> array(key: String, limit: Int, parse: (JSONObject) -> T): List<T> {
            val array = json.getJSONArray(key); require(array.length() <= limit)
            return List(array.length()) { parse(array.getJSONObject(it)) }
        }
        val history = array("history", EchoPersonalStateLimits.History) {
            val listened = it.getLong("listenedMs"); val played = it.getLong("playedAtEpochMs")
            val day = it.getLong("localEpochDay"); val hour = it.getInt("localHour")
            require(listened in 0..EchoPersonalStateLimits.MaxDurationMs && played in 0..4102444800000L && day in -365000..365000 && hour in 0..23)
            EchoPersonalListen(it.getJSONObject("track").track(), listened, played, day, hour)
        }
        val bookmarks = array("bookmarks", EchoPersonalStateLimits.Bookmarks) {
            val label = it.getString("label"); require(label.length <= 500)
            val position = it.position()
            val end = if (it.isNull("endPositionMs")) null else it.getLong("endPositionMs")
            require(end == null || end > position && end <= EchoPersonalStateLimits.MaxDurationMs)
            EchoPersonalBookmark(it.getJSONObject("track").track(), position, label, end)
        }
        val offsets = array("lyricsOffsets", EchoPersonalStateLimits.Offsets) {
            val offset = it.getLong("offsetMs"); require(offset in -30000..30000)
            EchoPersonalLyricsOffset(it.getJSONObject("track").track(), offset)
        }
        val resume = json.optJSONObject("resume")?.let { EchoPersonalResume(it.getJSONObject("track").track(), it.position()) }
        val revision = json.optString("revision"); require(revision.length <= 128)
        return EchoPersonalState(history, bookmarks, offsets, resume, revision)
    }
}
