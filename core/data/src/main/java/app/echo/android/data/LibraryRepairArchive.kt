package app.echo.android.data

import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibrarySource
import org.json.JSONArray
import org.json.JSONObject

/** Retains user references before a completed local scan removes a missing file from the index. */
internal suspend fun EchoLibraryRepository.archiveMissingLocalTracks(ids: List<String>) {
    val tracks = database.trackDao().getTracksByIds(ids).filter { it.source in listOf("mediastore", "saf") }
    val sql = database.openHelper.readableDatabase
    for (track in tracks) {
        val json = encodeRepairTrack(track)
        sql.query("SELECT favoritedAtEpochMs FROM library_favorites WHERE trackId = ?", arrayOf(track.id)).use {
            if (it.moveToFirst()) json.put("favoriteAt", it.getLong(0))
        }
        sql.query("SELECT playCount, lastPlayedAtEpochMs FROM library_playback_stats WHERE trackId = ?", arrayOf(track.id)).use {
            if (it.moveToFirst()) { json.put("playCount", it.getInt(0)); json.put("lastPlayed", it.getLong(1)) }
        }
        val refs = JSONArray()
        sql.query("SELECT playlistId, position FROM library_playlist_tracks WHERE trackId = ?", arrayOf(track.id)).use {
            while (it.moveToNext()) refs.put(JSONObject().put("id", it.getString(0)).put("position", it.getInt(1)))
        }
        json.put("playlists", refs)
        database.experienceDao().archive(LibraryRepairArchiveEntity(track.id, json.toString(), System.currentTimeMillis()))
    }
}

internal suspend fun EchoLibraryRepository.restoreRepairReferences(archive: LibraryRepairArchiveEntity) {
    val json = JSONObject(archive.payload)
    if (json.has("favoriteAt")) database.playlistDao().upsertFavorite(LibraryFavoriteEntity(archive.id, json.getLong("favoriteAt")))
    if (json.has("playCount")) database.openHelper.writableDatabase.execSQL(
        "INSERT OR REPLACE INTO library_playback_stats (trackId, playCount, lastPlayedAtEpochMs) VALUES (?, ? + COALESCE((SELECT playCount FROM library_playback_stats WHERE trackId = ?), 0), MAX(?, COALESCE((SELECT lastPlayedAtEpochMs FROM library_playback_stats WHERE trackId = ?), 0)))",
        arrayOf<Any>(archive.id, json.getInt("playCount"), archive.id, json.getLong("lastPlayed"), archive.id))
    val refs = json.optJSONArray("playlists") ?: return
    for (index in 0 until refs.length()) {
        val ref = refs.getJSONObject(index)
        if (database.playlistDao().getPlaylist(ref.getString("id")) != null) database.playlistDao().insertPlaylistTracks(
            listOf(LibraryPlaylistTrackEntity(ref.getString("id"), archive.id, ref.getInt("position"))))
    }
}

internal fun encodeRepairTrack(track: LibraryTrackEntity): JSONObject = JSONObject().apply {
    put("id", track.id); put("uri", track.contentUri); put("title", track.title); put("artist", track.artist)
    putOpt("album", track.album); putOpt("albumArtist", track.albumArtist); putOpt("artwork", track.artworkUri)
    put("duration", track.durationMs); putOpt("trackNumber", track.trackNumber); putOpt("discNumber", track.discNumber)
    putOpt("year", track.year); putOpt("mime", track.mimeType); put("size", track.sizeBytes)
    putOpt("rate", track.sampleRateHz); put("modified", track.dateModifiedSeconds); put("source", track.source)
    putOpt("path", track.relativePath); putOpt("editedAt", track.metadataEditedAtEpochMs)
    putOpt("genre", track.genre); putOpt("composer", track.composer)
    put("clipStart", track.clipStartMs); put("clipEnd", track.clipEndMs); putOpt("fileName", track.fileName)
}

internal fun decodeRepairTrack(archive: LibraryRepairArchiveEntity): LibraryTrackEntity {
    val json = JSONObject(archive.payload)
    fun optional(key: String) = if (json.has(key) && !json.isNull(key)) json.getString(key) else null
    fun number(key: String) = if (json.has(key) && !json.isNull(key)) json.getInt(key) else null
    return EchoTrack(archive.id, json.getString("uri"), json.getString("title"), json.getString("artist"),
        album = optional("album"), albumArtist = optional("albumArtist"), artworkUri = optional("artwork"),
        durationMs = json.getLong("duration"), trackNumber = number("trackNumber"), discNumber = number("discNumber"),
        year = number("year"), mimeType = optional("mime"), sizeBytes = json.getLong("size"), sampleRateHz = number("rate"),
        dateModifiedSeconds = json.getLong("modified"), source = LibrarySource(json.getString("source")),
        genre = optional("genre"), composer = optional("composer"), clipStartMs = json.optLong("clipStart"),
        clipEndMs = json.optLong("clipEnd")).toLibraryTrackEntity().copy(relativePath = optional("path"),
        metadataEditedAtEpochMs = if (json.has("editedAt")) json.getLong("editedAt") else null, fileName = optional("fileName"))
}
