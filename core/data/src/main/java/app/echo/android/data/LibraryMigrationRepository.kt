package app.echo.android.data

import androidx.room.withTransaction
import app.echo.android.model.backup.*
import app.echo.android.model.connect.EchoSyncTrackRef
import kotlinx.coroutines.*

suspend fun EchoLibraryRepository.exportListeningHistory(): List<EchoBackupHistoryEvent> = withContext(Dispatchers.IO) {
    val rows = database.playEventDao().backupEvents()
    require(rows.size <= 100000)
    rows.map { EchoBackupHistoryEvent(EchoBackupTrackRef(it.title,it.artist,durationMs = it.durationMs,album = it.album),
        it.listenedMs,it.playedAtEpochMs,it.localEpochDay,it.localHour,it.source) }
}

suspend fun EchoLibraryRepository.restoreListeningHistory(events: List<EchoBackupHistoryEvent>) = withContext(Dispatchers.IO) {
    require(events.size <= 100000)
    var imported = 0
    val ordered = withContext(Dispatchers.Default) { events.sortedBy { it.playedAtEpochMs } }
    val matches = linkedMapOf<EchoBackupTrackRef,LibraryTrackEntity?>()
    ordered.chunked(200).forEach { batch ->
        currentCoroutineContext().ensureActive()
        database.withTransaction {
            val dao = database.playEventDao()
            val existing = dao.migrationEvents(batch.first().playedAtEpochMs,batch.last().playedAtEpochMs).associateBy {
                Triple(it.playedAtEpochMs,it.title,it.artist to it.album)
            }.toMutableMap()
            for (event in batch) {
                val eventKey = Triple(event.playedAtEpochMs,event.track.title,event.track.artist to event.track.album)
                val previous = existing[eventKey]
                if (previous != null) dao.raiseListenedMs(previous.id,event.listenedMs)
                else {
                    if (!matches.containsKey(event.track)) {
                        matches[event.track] = uniqueSyncMatch(EchoSyncTrackRef(event.track.title,event.track.artist,event.track.album,event.track.durationMs))
                        if (matches.size > 256) matches.remove(matches.keys.first())
                    }
                    val matched = matches[event.track]
                    val fallback = "history:" + java.security.MessageDigest.getInstance("SHA-256").digest("${event.track.title}\n${event.track.artist}\n${event.track.album}".toByteArray()).joinToString("") { "%02x".format(it) }
                    val record = LibraryPlayEventEntity(trackId = matched?.id ?: fallback,title = event.track.title,artist = event.track.artist,
                        album = event.track.album,artworkUri = matched?.artworkUri,source = event.source,durationMs = event.track.durationMs,
                        listenedMs = event.listenedMs,playedAtEpochMs = event.playedAtEpochMs,localEpochDay = event.localEpochDay,localHour = event.localHour)
                    val id = dao.insert(record)
                    existing[eventKey] = record.copy(id = id)
                    imported++
                }
            }
        }
        yield()
    }
    imported
}

suspend fun EchoLibraryRepository.backupTrackReferences(ids: List<String>): Map<String,EchoBackupTrackRef> = withContext(Dispatchers.IO) {
    val result = ids.distinct().chunked(500).flatMap { database.trackDao().getTracksByIds(it) }.associate { it.id to EchoBackupTrackRef(it.title,it.artist,it.relativePath,it.durationMs,it.album) }.toMutableMap()
    ids.filterNot(result::containsKey).forEach { id -> database.experienceDao().archived(id)?.let { archive ->
        val row = decodeRepairTrack(archive); result[id] = EchoBackupTrackRef(row.title,row.artist,row.relativePath,row.durationMs,row.album)
    } }
    result
}

suspend fun EchoLibraryRepository.matchLegacyLyricHashes(hashes: Set<String>): Map<String,String> = withContext(Dispatchers.IO) {
    val result = mutableMapOf<String,String>()
    var after = ""
    val hex = "0123456789abcdef"
    while (result.size < hashes.size) {
        currentCoroutineContext().ensureActive()
        val ids = database.trackDao().backupIdsAfter(after)
        if (ids.isEmpty()) break
        ids.forEach { id ->
            val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(id.toByteArray())
            val hash = buildString(64) { bytes.forEach { byte -> val value = byte.toInt() and 255; append(hex[value ushr 4]); append(hex[value and 15]) } }
            if (hash in hashes) result[hash] = id
        }
        after = ids.last(); yield()
    }
    result
}

suspend fun EchoLibraryRepository.resolveBackupTrack(ref: EchoBackupTrackRef): String? = withContext(Dispatchers.IO) {
    uniqueSyncMatch(EchoSyncTrackRef(ref.title,ref.artist,ref.album,ref.durationMs))?.id
}

suspend fun EchoLibraryRepository.backupPreview(document: EchoBackupDocument, current: EchoBackupSettings): EchoBackupPreview = withContext(Dispatchers.IO) {
    val refs = (document.playlists.flatMap { it.tracks } + document.favorites + document.bookmarks.map { it.track } + document.lyrics.map { it.track }).distinct()
    var matched = 0; var missing = 0
    for (ref in refs) { currentCoroutineContext().ensureActive(); if (resolveBackupTrack(ref) == null) missing++ else matched++ }
    val changed = mutableListOf<String>()
    val settings = document.settings
    if (settings.homeLayout != null && settings.homeLayout != current.homeLayout) changed += "home"
    if (settings.playerPageStyle != null && (settings.playerPageStyle != current.playerPageStyle || settings.playerTextScale != current.playerTextScale || settings.playerArtworkScale != current.playerArtworkScale)) changed += "player"
    if (settings.themeMode != null && (settings.themeMode != current.themeMode || settings.colorTheme != current.colorTheme)) changed += "theme"
    if (settings.appLanguage != null && settings.appLanguage != current.appLanguage) changed += "language"
    if (settings.backgroundMode != null) changed += "background"
    if (settings.lyricsFontFamily != null && settings.lyricsFontFamily != current.lyricsFontFamily) changed += "lyrics"
    EchoBackupPreview(database.playlistDao().getPlaylistsBySource("mediastore").size,document.playlists.size,document.favorites.size,
        document.bookmarks.size,document.history.size,document.lyrics.size,document.assets.size,matched,missing,changed)
}
