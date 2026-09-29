package app.echo.android.data

import androidx.room.withTransaction
import app.echo.android.model.playback.ListeningStats
import app.echo.android.model.playback.ListeningStatsEntry
import app.echo.android.model.playback.ListeningStatsRange
import app.echo.android.model.playback.PlaybackHeatmapDay
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 听歌历史的读写入口。聚合都在 SQL 里完成，不把整表读进内存。 */
class ListeningHistoryRepository(
    private val database: EchoLibraryDatabase,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao: LibraryPlayEventDao get() = database.playEventDao()

    suspend fun recordListen(listen: ListeningHistoryListen): Long {
        val local = Instant.ofEpochMilli(listen.startedAtEpochMs).atZone(zone())
        return dao.insert(
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

    suspend fun clear() = dao.clear()

    private companion object {
        const val DEFAULT_RANK_LIMIT = 10
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

private fun PlayEventRankRow.toEntry(): ListeningStatsEntry =
    ListeningStatsEntry(
        key = rankKey,
        title = title,
        subtitle = subtitle?.takeIf(String::isNotBlank),
        artworkUri = artworkUri,
        playCount = playCount,
        listenedMs = listenedMs,
    )
