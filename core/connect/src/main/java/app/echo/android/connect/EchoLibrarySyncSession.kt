package app.echo.android.connect

import app.echo.android.model.connect.*
import org.json.JSONArray
import org.json.JSONObject

interface EchoLibrarySyncSession {
    suspend fun snapshot(key: String): EchoSyncState = throw EchoSyncUnsupportedException()
    suspend fun replace(expected: String,desired: EchoSyncState): EchoSyncApplyResult = throw EchoSyncUnsupportedException()
    suspend fun collections(): List<EchoSyncCollection>
    suspend fun exportBatch(collection: EchoSyncCollection, offset: Int): EchoSyncBatch
    suspend fun importBatch(batch: EchoSyncBatch, preview: Boolean): EchoSyncBatchResult
}

internal fun EchoSyncState.stateJson() = JSONObject().put("key",key).put("name",name).put("exists",exists).put("revision",revision)
    .put("tracks",JSONArray().apply { tracks.forEach { put(JSONObject().put("title",it.title).put("artist",it.artist).put("album",it.album ?: JSONObject.NULL).put("durationMs",it.durationMs)) } })
internal fun JSONObject.syncState(): EchoSyncState {
    val array = getJSONArray("tracks"); require(array.length() <= 10000)
    val tracks = List(array.length()) { index -> val ref = array.getJSONObject(index)
        EchoSyncTrackRef(ref.getString("title"),ref.getString("artist"),if (ref.isNull("album")) null else ref.optString("album"),ref.getLong("durationMs")) }
    val key = getString("key"); val name = optString("name")
    require(key.length in 1..512 && name.length <= 100)
    return EchoSyncState(key,name,getBoolean("exists"),tracks,optString("revision"))
}

internal fun JSONObject.syncCollection(): EchoSyncCollection {
    val key = getString("key"); val name = optString("name", "")
    val count = getInt("trackCount")
    require(key.length in 1..512 && name.length <= 100 && count in 0..1000000)
    return EchoSyncCollection(key, name, count, optBoolean("favorites"))
}

internal fun EchoSyncCollection.syncJson() = JSONObject().put("key", key).put("name", name)
    .put("trackCount", trackCount).put("favorites", favorites)

internal fun EchoSyncBatch.syncJson(preview: Boolean) = JSONObject().put("version", 1).put("preview", preview)
    .put("collection", collection.syncJson()).put("tracks", JSONArray().apply { tracks.forEach { track ->
        put(JSONObject().put("title", track.title).put("artist", track.artist)
            .put("album", track.album ?: JSONObject.NULL).put("durationMs", track.durationMs))
    } })

internal fun JSONObject.syncTracks(): List<EchoSyncTrackRef> {
    val tracks = getJSONArray("tracks")
    require(tracks.length() <= 200)
    return List(tracks.length()) { index ->
        val track = tracks.getJSONObject(index)
        val title = track.getString("title"); val artist = track.getString("artist")
        val album = if (track.isNull("album")) null else track.optString("album")
        val duration = track.getLong("durationMs")
        require(title.length in 1..500 && artist.length <= 500 && (album?.length ?: 0) <= 500 && duration >= 0)
        EchoSyncTrackRef(title, artist, album, duration)
    }
}
