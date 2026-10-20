package app.echo.android.data

import app.echo.android.model.connect.*
import app.echo.android.model.backup.EchoBackupHistoryEvent
import app.echo.android.model.backup.EchoBackupTrackRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Bounded snapshots on explicit sync; no background full-library scan. */
suspend fun EchoLibraryRepository.personalStateRecords(): EchoPersonalState = withContext(Dispatchers.IO) {
    val history = database.openHelper.readableDatabase.query(
        "SELECT title, artist, album, durationMs, listenedMs, playedAtEpochMs, localEpochDay, localHour FROM library_play_events ORDER BY playedAtEpochMs DESC, id DESC LIMIT ${EchoPersonalStateLimits.History}",
    ).use { cursor -> buildList {
        while (cursor.moveToNext()) {
            val track = EchoSyncTrackRef(cursor.getString(0), cursor.getString(1), if (cursor.isNull(2)) null else cursor.getString(2), cursor.getLong(3))
            add(EchoPersonalListen(track, cursor.getLong(4), cursor.getLong(5), cursor.getLong(6), cursor.getInt(7)))
        }
    } }
    val ranges = PersonalBookmarkRangeStore(appContext)
    val ends = ranges.snapshot()
    val bookmarks = database.openHelper.readableDatabase.query(
        "SELECT b.positionMs, b.label, t.title, t.artist, t.album, t.durationMs, b.trackId FROM library_bookmarks b JOIN library_tracks t ON t.id = b.trackId ORDER BY b.id LIMIT ${EchoPersonalStateLimits.Bookmarks}",
    ).use { cursor -> buildList {
        while (cursor.moveToNext()) add(EchoPersonalBookmark(
            EchoSyncTrackRef(cursor.getString(2), cursor.getString(3), if (cursor.isNull(4)) null else cursor.getString(4), cursor.getLong(5)), cursor.getLong(0), cursor.getString(1), ends[ranges.key(cursor.getString(6), cursor.getLong(0))]))
    } }
    EchoPersonalState(history = history, bookmarks = bookmarks)
}

suspend fun EchoLibraryRepository.matchPersonalStateTrack(ref: EchoSyncTrackRef) = withContext(Dispatchers.IO) {
    val rows = database.trackDao().queryTracks(androidx.sqlite.db.SimpleSQLiteQuery(
        "SELECT * FROM library_tracks WHERE normalizedTitle = ? ORDER BY id LIMIT 101", arrayOf(ref.title.normalizedForSearch())))
    if (rows.size > 100) return@withContext null
    fun same(left: String?, right: String?) = left.orEmpty().trim().equals(right.orEmpty().trim(), ignoreCase = true)
    val candidates = rows.filter { same(it.title, ref.title) && same(it.artist, ref.artist) && same(it.album, ref.album) &&
        kotlin.math.abs(it.durationMs - ref.durationMs) <= 2000L }.map { it.toEchoTrack() }
    val anchor = candidates.firstOrNull() ?: return@withContext null
    if (candidates.size == 1) return@withContext anchor
    // Multiple sources are safe only when the same conservative version policy relates every row.
    if (!candidates.all { app.echo.android.model.library.EchoTrackVersionPolicy.matches(anchor, it) }) return@withContext null
    TrackVersionsRepository(database, appContext).resolvePreferredTrackVersions(listOf(anchor)) { true }.single()
}

suspend fun EchoLibraryRepository.applyPersonalStateRecords(state: EchoPersonalState): EchoPersonalStateResult = withContext(Dispatchers.IO) {
    require(state.history.size <= EchoPersonalStateLimits.History && state.bookmarks.size <= EchoPersonalStateLimits.Bookmarks)
    val added = restoreListeningHistory(state.history.map {
        EchoBackupHistoryEvent(EchoBackupTrackRef(it.track.title, it.track.artist, durationMs = it.track.durationMs, album = it.track.album),
            it.listenedMs, it.playedAtEpochMs, it.localEpochDay, it.localHour, "echo-link")
    })
    var bookmarks = 0; var missing = 0; var unsupported = 0
    for (bookmark in state.bookmarks) {
        coroutineContext.ensureActive()
        val track = matchPersonalStateTrack(bookmark.track)
        if (track == null || track.durationMs > 0 && bookmark.positionMs >= track.durationMs) missing++
        else if (bookmark.label.trim().length > 100) unsupported++
        else {
            val label = bookmark.label.trim().ifBlank { track.title.take(100) }
            saveBookmark(track, bookmark.positionMs, label)
            PersonalBookmarkRangeStore(appContext).set(track.id, bookmark.positionMs, bookmark.endPositionMs)
            bookmarks++
        }
    }
    EchoPersonalStateResult(added, bookmarks, 0, missing, unsupported)
}
