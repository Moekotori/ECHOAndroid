package app.echo.android.data

import androidx.paging.*
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private fun momentQuery(query: String, limit: Int? = null): SimpleSQLiteQuery {
    val pattern = "%${query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"
    return SimpleSQLiteQuery("""SELECT b.id, b.trackId, b.positionMs, b.label,
        COALESCE(t.title,b.titleSnapshot) AS title, COALESCE(t.artist,b.artistSnapshot) AS artist,
        COALESCE(t.contentUri,b.uriSnapshot) AS uri, t.artworkUri,
        COALESCE(t.durationMs,b.durationSnapshot) AS durationMs, t.source
        FROM library_bookmarks b LEFT JOIN library_tracks t ON t.id = b.trackId
        WHERE b.label LIKE ? ESCAPE '\' OR COALESCE(t.title,b.titleSnapshot) LIKE ? ESCAPE '\'
           OR COALESCE(t.artist,b.artistSnapshot) LIKE ? ESCAPE '\'
        ORDER BY b.trackId, b.positionMs, b.id""" + (limit?.let { " LIMIT ${it.coerceIn(1, 100)}" } ?: ""), arrayOf(pattern, pattern, pattern))
}

fun EchoLibraryRepository.pagedMoments(query: String) = Pager(PagingConfig(pageSize = 40),
    pagingSourceFactory = { database.experienceDao().pageMoments(momentQuery(query)) }).flow.map { page -> page.map { it.toMoment() } }

suspend fun EchoLibraryRepository.searchMoments(query: String) = withContext(Dispatchers.IO) {
    database.experienceDao().queryMoments(momentQuery(query, 30)).map { it.toMoment() }
}
