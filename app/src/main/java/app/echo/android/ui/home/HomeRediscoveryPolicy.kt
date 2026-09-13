package app.echo.android.ui.home

import app.echo.android.data.LibraryAlbumListenStatsRow
import app.echo.android.data.toAlbumSummary
import app.echo.android.model.library.AlbumSummary

/** Bounded local history candidates; never label an unplayed album as rediscovered. */
internal fun rediscoverHomeAlbums(rows: List<LibraryAlbumListenStatsRow>, nowEpochMs: Long): List<AlbumSummary> {
    val cutoff = nowEpochMs - 30L * 24 * 60 * 60 * 1000
    return rows.asSequence()
        .filter { it.playCount > 0 && it.lastPlayedAtEpochMs > 0 && it.lastPlayedAtEpochMs <= cutoff }
        .sortedWith(compareBy<LibraryAlbumListenStatsRow> { it.lastPlayedAtEpochMs }.thenBy { it.albumKey })
        .distinctBy { it.albumKey }
        .take(6)
        .map { it.toAlbumSummary() }
        .toList()
}
