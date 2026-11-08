package app.echo.android.data

import androidx.room.withTransaction
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.map
import app.echo.android.model.playback.PlaybackHistoryEntry
import app.echo.android.model.playback.PlaybackHistoryFilter
import app.echo.android.model.playback.PlaybackHistorySummary
import app.echo.android.model.playback.ListeningStats
import app.echo.android.model.playback.ListeningStatsEntry
import app.echo.android.model.playback.ListeningStatsRange
import app.echo.android.model.playback.PlaybackHeatmapDay
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 听歌历史的读写入口。聚合都在 SQL 里完成，不把整表读进内存。 */
class ListeningHistoryRepository(
    private val database: EchoLibraryDatabase,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao: LibraryPlayEventDao get() = database.playEventDao()
    private val writes = Mutex()
    private var clearedThroughEpochMs = Long.MIN_VALUE

    fun pagedHistory(filter: PlaybackHistoryFilter) = Pager(
        config = PagingConfig(pageSize = 40, initialLoadSize = 40, maxSize = 160, enablePlaceholders = false),
        pagingSourceFactory = { dao.pageHistory(playbackHistoryQuery(filter, todayEpochDay())) },
    ).flow.map { page -> page.map(PlayEventHistoryRow::toEntry) }

    /** Aggregates for the whole filter, independent of how far the list has been paged. */
    fun observeSummary(filter: PlaybackHistoryFilter): Flow<PlaybackHistorySummary> =
        dao.observeSummary(playbackHistorySummaryQuery(filter, todayEpochDay())).map {
            PlaybackHistorySummary(it.plays, it.listenedMs, it.tracks, it.latestPlayedAtEpochMs)
        }

    /** Newest-first rows of the current filter for export; bounded so a huge history cannot exhaust memory. */
    suspend fun exportRows(filter: PlaybackHistoryFilter, limit: Int = EXPORT_LIMIT): List<PlaybackHistoryEntry> =
        dao.historyRows(playbackHistoryQuery(filter, todayEpochDay(), limit)).map(PlayEventHistoryRow::toEntry)

    /** Remove records whose track left the library. Returns how many were deleted. */
    suspend fun deleteUnavailable(): Int = writes.withLock { dao.deleteUnavailable() }

    private fun todayEpochDay(): Long = Instant.ofEpochMilli(now()).atZone(zone()).toLocalDate().toEpochDay()

    suspend fun deleteEvent(id: Long) = dao.deleteEvent(id)

    suspend fun recordListen(listen: ListeningHistoryListen): Long = writes.withLock {
        // A listen that started before Clear must not reappear from a queued recorder write.
        if (listen.startedAtEpochMs <= clearedThroughEpochMs) return@withLock 0L
        val local = Instant.ofEpochMilli(listen.startedAtEpochMs).atZone(zone())
        dao.insert(
            LibraryPlayEventEntity(
                trackId = listen.trackId,
                title = listen.title,
                artist = listen.artist,
                album = listen.album,
                artworkUri = listen.artworkUri,
                source = listen.source,
                durationMs = listen.durationMs.coerceAtLeast(0L),
                listenedMs = listen.listenedMs.coerceAtLeast(0L),
                playedAtEpochMs = listen.startedAtEpochMs,
                localEpochDay = local.toLocalDate().toEpochDay(),
                localHour = local.hour,
            ),
        )
    }

    /** 同一次收听继续累计时长；只会增大，重复调用无副作用。 */
    suspend fun updateListenedMs(eventId: Long, listenedMs: Long) {
        if (eventId <= 0L) return
        dao.raiseListenedMs(eventId, listenedMs.coerceAtLeast(0L))
    }

    fun observeHeatmap(visibleDays: Long): Flow<List<PlaybackHeatmapDay>> {
        val today = Instant.ofEpochMilli(now()).atZone(zone()).toLocalDate().toEpochDay()
        return dao.observeDailyCounts(today - visibleDays + 1).map { rows ->
            rows.map { PlaybackHeatmapDay(epochDay = it.epochDay, playCount = it.playCount) }
        }
    }

    suspend fun stats(range: ListeningStatsRange, limit: Int = DEFAULT_RANK_LIMIT): ListeningStats {
        val from = ListeningHistoryPolicy.rangeStartEpochMs(range, now(), zone())
        return database.withTransaction {
            val totals = dao.totals(from)
            val hours = IntArray(24)
            dao.hourlyCounts(from).forEach { row ->
                if (row.hour in 0..23) hours[row.hour] = row.playCount
            }
            ListeningStats(
                range = range,
                totalPlays = totals.plays,
                totalListenedMs = totals.listenedMs,
                activeDays = totals.activeDays,
                topTracks = dao.topTracks(from, limit).map(PlayEventRankRow::toEntry),
                topAlbums = dao.topAlbums(from, limit).map(PlayEventRankRow::toEntry),
                topArtists = dao.topArtists(from, limit).map(PlayEventRankRow::toEntry),
                playsByHour = hours.toList(),
            )
        }
    }

    suspend fun clear() = writes.withLock {
        dao.clear()
        clearedThroughEpochMs = now()
    }

    suspend fun monthlyRecap(year: Int, month: Int): app.echo.android.model.playback.EchoMonthlyRecap =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val period = MonthlyRecapPeriod.of(year, month)
            val current = java.time.YearMonth.from(Instant.ofEpochMilli(now()).atZone(zone()))
            require(java.time.YearMonth.of(year, month) <= current)
            database.withTransaction {
                val totals = dao.monthlyTotals(period.fromDay, period.untilDay)
                val hours = IntArray(24)
                dao.monthlyHours(period.fromDay, period.untilDay).forEach { if (it.hour in 0..23) hours[it.hour] = it.playCount }
                app.echo.android.model.playback.EchoMonthlyRecap(year, month, java.time.YearMonth.of(year, month) == current,
                    totals.plays, totals.listenedMs, totals.activeDays,
                    dao.monthlyNewArtistCount(period.fromDay, period.untilDay),
                    dao.monthlyNewArtists(period.fromDay, period.untilDay, 5).map(PlayEventRankRow::toEntry),
                    dao.monthlyAlbums(period.fromDay, period.untilDay, 5).map(PlayEventRankRow::toEntry), hours.toList())
            }
        }

    private companion object {
        const val DEFAULT_RANK_LIMIT = 10
        const val EXPORT_LIMIT = 5_000
    }
}

data class ListeningHistoryListen(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUri: String?,
    val source: String?,
    val durationMs: Long,
    val listenedMs: Long,
    val startedAtEpochMs: Long,
)

private fun PlayEventHistoryRow.toEntry() = PlaybackHistoryEntry(
    event.id, event.trackId, event.title, event.artist, event.album, event.artworkUri,
    event.playedAtEpochMs, event.listenedMs, canReplay, plays,
)

private fun PlayEventRankRow.toEntry(): ListeningStatsEntry =
    ListeningStatsEntry(
        key = rankKey,
        title = title,
        subtitle = subtitle?.takeIf(String::isNotBlank),
        artworkUri = artworkUri,
        playCount = playCount,
        listenedMs = listenedMs,
    )
