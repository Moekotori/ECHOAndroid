package app.echo.android.data

import androidx.sqlite.db.SimpleSQLiteQuery
import app.echo.android.model.backup.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun EchoLibraryRepository.exportBackupBookmarks(): List<EchoBackupBookmark> = withContext(Dispatchers.IO) {
    val bookmarks = database.experienceDao().exportBookmarks()
    if (bookmarks.size > 10000) throw EchoBackupException("A backup can contain at most 10,000 saved moments")
    val tracks = bookmarks.map { it.trackId }.distinct().chunked(500)
        .flatMap { database.trackDao().getTracksByIds(it) }.associateBy { it.id }
    bookmarks.mapNotNull { bookmark ->
        val track = tracks[bookmark.trackId] ?: database.experienceDao().archived(bookmark.trackId)?.let(::decodeRepairTrack)
            ?: return@mapNotNull null
        EchoBackupBookmark(EchoBackupTrackRef(track.title, track.artist, track.relativePath, track.durationMs), bookmark.positionMs, bookmark.label)
    }
}

suspend fun EchoLibraryRepository.restoreBackupBookmarks(bookmarks: List<EchoBackupBookmark>): Int = withContext(Dispatchers.IO) {
    require(bookmarks.size <= 10000)
    var missing = 0
    for (bookmark in bookmarks) {
        val ref = bookmark.track
        val track = uniqueSyncMatch(app.echo.android.model.connect.EchoSyncTrackRef(ref.title, ref.artist, null, ref.durationMs))
        if (track != null) saveBookmark(track.id, bookmark.positionMs, bookmark.label) else missing++
    }
    missing
}
