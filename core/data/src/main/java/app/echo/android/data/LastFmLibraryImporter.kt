package app.echo.android.data

import androidx.room.withTransaction
import app.echo.android.model.settings.LastFmImportPhase
import app.echo.android.model.settings.LastFmImportStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class LastFmLibraryImporter(private val database: EchoLibraryDatabase, private val client: LastFmClient = LastFmClient()) {
    suspend fun run(credentials: LastFmCredentials, username: String, report: (LastFmImportStatus) -> Unit) = withContext(Dispatchers.IO) {
        var status = LastFmImportStatus(phase = LastFmImportPhase.Running)
        report(status)
        try {
            for (loved in listOf(true, false)) {
                var page = 1
                var totalPages = 1
                while (page <= totalPages) {
                    currentCoroutineContext().ensureActive()
                    val result = client.importPage(credentials, username, loved, page)
                    if (page == 1) totalPages = result.totalPages
                    val tracks = result.tracks
                    if (tracks.isNotEmpty()) {
                        var committed = status
                        database.withTransaction {
                            val candidates = database.lastFmDao().matchCandidates(tracks.map { it.title.normalizedForSearch() }.distinct())
                            check(candidates.size <= 10_000) { "Too many local editions to match safely" }
                            val index = candidates.groupBy { lastFmMatchKey(it.title, it.artist) }
                            for (track in tracks) {
                                currentCoroutineContext().ensureActive()
                                val matches = index[lastFmMatchKey(track.title, track.artist)].orEmpty()
                                committed = when (matches.size) {
                                    0 -> committed.copy(unmatched = committed.unmatched + 1)
                                    1 -> {
                                        val dao = database.lastFmDao()
                                        val id = matches.single().id
                                        val added = track.loved && dao.addFavorite(LibraryFavoriteEntity(id, System.currentTimeMillis())) != -1L
                                        val raised = if (track.playCount > 0) {
                                            val inserted = dao.addStats(LibraryPlaybackStatsEntity(id, track.playCount, 0)) != -1L
                                            inserted || dao.raisePlayCount(id, track.playCount) > 0
                                        } else false
                                        committed.copy(lovedAdded = committed.lovedAdded + if (added) 1 else 0,
                                            playCountsUpdated = committed.playCountsUpdated + if (raised) 1 else 0)
                                    }
                                    else -> committed.copy(ambiguous = committed.ambiguous + 1)
                                }
                            }
                        }
                        status = committed
                    }
                    status = status.copy(processed = status.processed + tracks.size)
                    report(status)
                    page++
                    if (page <= totalPages) delay(200)
                }
            }
            report(status.copy(phase = LastFmImportPhase.Completed))
        } catch (error: CancellationException) {
            report(status.copy(phase = LastFmImportPhase.Cancelled))
            throw error
        } catch (error: Exception) {
            report(status.copy(phase = LastFmImportPhase.Failed))
            throw error
        }
    }
}
