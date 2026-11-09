package app.echo.android.data

import androidx.sqlite.db.SimpleSQLiteQuery
import app.echo.android.model.library.EchoTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Probe random row IDs using indexed lookups. Sparse or exhausted libraries get one fallback query. */
suspend fun LibraryTrackDao.sampleLocalShuffleTrack(currentId: String, recentIds: List<String>): EchoTrack? =
    withContext(Dispatchers.IO) {
        val first = firstShuffleRowId() ?: return@withContext null
        val last = lastShuffleRowId() ?: return@withContext null
        if (first > last) return@withContext null
        val recent = recentIds.takeLast(100).distinctLastPlayed().filterNot { it == currentId }
        val excluded = listOf(currentId) + recent
        val placeholders = excluded.joinToString { "?" }
        val eligible = "SELECT * FROM visible_library_tracks WHERE ${LibraryScanPolicy.LocalSourceSql}"
        repeat(2) {
            val rowIds = List(24) { (first..last).random() }.distinct()
            val rowPlaceholders = rowIds.joinToString { "?" }
            val bindings = buildList<Any> { addAll(rowIds); addAll(excluded) }
            val sample = queryTracks(SimpleSQLiteQuery(
                "$eligible AND id IN (SELECT id FROM library_tracks WHERE rowid IN ($rowPlaceholders)) AND id NOT IN ($placeholders)",
                bindings.toTypedArray(),
            )).randomOrNull()
            if (sample != null) return@withContext sample.toEchoTrack()
        }
        val fresh = queryTracks(SimpleSQLiteQuery(
            "$eligible AND id NOT IN ($placeholders) ORDER BY RANDOM() LIMIT 1", excluded.toTypedArray(),
        )).firstOrNull()
        if (fresh != null) return@withContext fresh.toEchoTrack()
        if (recent.isEmpty()) return@withContext null
        val available = queryTracks(SimpleSQLiteQuery(
            "$eligible AND id IN (${recent.joinToString { "?" }})", recent.toTypedArray(),
        )).associateBy { it.id }
        recent.firstNotNullOfOrNull(available::get)?.toEchoTrack()
    }

private fun List<String>.distinctLastPlayed(): List<String> = asReversed().distinct().asReversed()
