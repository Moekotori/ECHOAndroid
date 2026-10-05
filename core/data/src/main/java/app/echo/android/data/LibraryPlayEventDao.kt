package app.echo.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Embedded
import androidx.room.RawQuery
import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

data class PlayEventDayCountRow(
    val epochDay: Long,
    val playCount: Int,
)

data class PlayEventHourCountRow(
    val hour: Int,
    val playCount: Int,
)

data class PlayEventTotalsRow(
    val plays: Int,
    val listenedMs: Long,
    val activeDays: Int,
)

data class PlayEventRankRow(
    val rankKey: String,
    val title: String,
    val subtitle: String?,
    val artworkUri: String?,
    val playCount: Int,
    val listenedMs: Long,
)

data class PlayEventHistoryRow(
    @Embedded val event: LibraryPlayEventEntity,
    val canReplay: Boolean,
)

@Dao
interface LibraryPlayEventDao {
    @RawQuery(observedEntities = [LibraryPlayEventEntity::class, LibraryTrackEntity::class])
    fun pageHistory(query: SupportSQLiteQuery): PagingSource<Int, PlayEventHistoryRow>

    @Query("DELETE FROM library_play_events WHERE id = :id")
    suspend fun deleteEvent(id: Long)

    @Insert
    suspend fun insert(event: LibraryPlayEventEntity): Long

    @Query("SELECT * FROM library_play_events ORDER BY playedAtEpochMs, id LIMIT 100001")
    suspend fun backupEvents(): List<LibraryPlayEventEntity>

    @Query("SELECT * FROM library_play_events WHERE playedAtEpochMs BETWEEN :from AND :to ORDER BY playedAtEpochMs")
    suspend fun migrationEvents(from: Long,to: Long): List<LibraryPlayEventEntity>

    @Query("SELECT id FROM library_play_events WHERE playedAtEpochMs = :played AND title = :title AND artist = :artist AND album IS :album LIMIT 1")
    suspend fun matchingEvent(played: Long, title: String, artist: String, album: String?): Long?

    @Query("UPDATE library_play_events SET listenedMs = :listenedMs WHERE id = :id AND listenedMs < :listenedMs")
    suspend fun raiseListenedMs(id: Long, listenedMs: Long): Int

    @Query(
        """
        SELECT localEpochDay AS epochDay, COUNT(*) AS playCount
        FROM library_play_events
        WHERE localEpochDay >= :fromEpochDay
        GROUP BY localEpochDay
        ORDER BY localEpochDay
        """,
    )
    fun observeDailyCounts(fromEpochDay: Long): Flow<List<PlayEventDayCountRow>>

    @Query(
        """
        SELECT COUNT(*) AS plays,
               COALESCE(SUM(listenedMs), 0) AS listenedMs,
               COUNT(DISTINCT localEpochDay) AS activeDays
        FROM library_play_events
        WHERE playedAtEpochMs >= :fromEpochMs
        """,
    )
    suspend fun totals(fromEpochMs: Long): PlayEventTotalsRow

    @Query(
        """
        SELECT localHour AS hour, COUNT(*) AS playCount
        FROM library_play_events
        WHERE playedAtEpochMs >= :fromEpochMs
        GROUP BY localHour
        """,
    )
    suspend fun hourlyCounts(fromEpochMs: Long): List<PlayEventHourCountRow>

    @Query(
        """
        SELECT trackId AS rankKey,
               MAX(title) AS title,
               MAX(artist) AS subtitle,
               MAX(artworkUri) AS artworkUri,
               COUNT(*) AS playCount,
               COALESCE(SUM(listenedMs), 0) AS listenedMs
        FROM library_play_events
        WHERE playedAtEpochMs >= :fromEpochMs
        GROUP BY trackId
        ORDER BY playCount DESC, listenedMs DESC
        LIMIT :limit
        """,
    )
    suspend fun topTracks(fromEpochMs: Long, limit: Int): List<PlayEventRankRow>

    @Query(
        """
        SELECT LOWER(TRIM(album)) AS rankKey,
               MAX(album) AS title,
               MAX(artist) AS subtitle,
               MAX(artworkUri) AS artworkUri,
               COUNT(*) AS playCount,
               COALESCE(SUM(listenedMs), 0) AS listenedMs
        FROM library_play_events
        WHERE playedAtEpochMs >= :fromEpochMs AND album IS NOT NULL AND TRIM(album) != ''
        GROUP BY LOWER(TRIM(album))
        ORDER BY playCount DESC, listenedMs DESC
        LIMIT :limit
        """,
    )
    suspend fun topAlbums(fromEpochMs: Long, limit: Int): List<PlayEventRankRow>

    @Query(
        """
        SELECT LOWER(TRIM(artist)) AS rankKey,
               MAX(artist) AS title,
               NULL AS subtitle,
               MAX(artworkUri) AS artworkUri,
               COUNT(*) AS playCount,
               COALESCE(SUM(listenedMs), 0) AS listenedMs
        FROM library_play_events
        WHERE playedAtEpochMs >= :fromEpochMs AND TRIM(artist) != ''
        GROUP BY LOWER(TRIM(artist))
        ORDER BY playCount DESC, listenedMs DESC
        LIMIT :limit
        """,
    )
    suspend fun topArtists(fromEpochMs: Long, limit: Int): List<PlayEventRankRow>

    @Query("""
        SELECT COUNT(*) AS plays, COALESCE(SUM(listenedMs), 0) AS listenedMs,
               COUNT(DISTINCT localEpochDay) AS activeDays
        FROM library_play_events WHERE localEpochDay >= :fromDay AND localEpochDay < :untilDay
    """)
    suspend fun monthlyTotals(fromDay: Long, untilDay: Long): PlayEventTotalsRow

    @Query("""
        SELECT localHour AS hour, COUNT(*) AS playCount FROM library_play_events
        WHERE localEpochDay >= :fromDay AND localEpochDay < :untilDay
        GROUP BY localHour
    """)
    suspend fun monthlyHours(fromDay: Long, untilDay: Long): List<PlayEventHourCountRow>

    @Query("""
        SELECT COUNT(*) FROM (
            SELECT LOWER(TRIM(artist)) FROM library_play_events WHERE TRIM(artist) != ''
            GROUP BY LOWER(TRIM(artist)) HAVING MIN(localEpochDay) >= :fromDay AND MIN(localEpochDay) < :untilDay
        )
    """)
    suspend fun monthlyNewArtistCount(fromDay: Long, untilDay: Long): Int

    @Query("""
        SELECT LOWER(TRIM(artist)) AS rankKey, MAX(artist) AS title, NULL AS subtitle,
               MAX(CASE WHEN localEpochDay >= :fromDay AND localEpochDay < :untilDay THEN artworkUri END) AS artworkUri,
               SUM(CASE WHEN localEpochDay >= :fromDay AND localEpochDay < :untilDay THEN 1 ELSE 0 END) AS playCount,
               COALESCE(SUM(CASE WHEN localEpochDay >= :fromDay AND localEpochDay < :untilDay THEN listenedMs ELSE 0 END), 0) AS listenedMs
        FROM library_play_events WHERE TRIM(artist) != ''
        GROUP BY LOWER(TRIM(artist))
        HAVING MIN(localEpochDay) >= :fromDay AND MIN(localEpochDay) < :untilDay
        ORDER BY playCount DESC, listenedMs DESC, rankKey LIMIT :limit
    """)
    suspend fun monthlyNewArtists(fromDay: Long, untilDay: Long, limit: Int): List<PlayEventRankRow>

    @Query("""
        WITH first_times AS (
            SELECT trackId, MIN(playedAtEpochMs) AS firstAt FROM library_play_events GROUP BY trackId
        ), first_events AS (
            SELECT e.trackId, MIN(e.id) AS firstId FROM library_play_events e
            JOIN first_times f ON f.trackId = e.trackId AND f.firstAt = e.playedAtEpochMs GROUP BY e.trackId
        )
        SELECT COALESCE(NULLIF(t.albumKey, ''), LOWER(TRIM(e.album)) || char(31) || LOWER(TRIM(e.artist))) AS rankKey,
               MAX(e.album) AS title, MAX(COALESCE(NULLIF(t.albumArtist, ''), e.artist)) AS subtitle,
               MAX(e.artworkUri) AS artworkUri,
               SUM(CASE WHEN e.id != initial_listen.firstId THEN 1 ELSE 0 END) AS playCount,
               COALESCE(SUM(CASE WHEN e.id != initial_listen.firstId THEN e.listenedMs ELSE 0 END), 0) AS listenedMs
        FROM library_play_events e JOIN first_events initial_listen ON initial_listen.trackId = e.trackId
        LEFT JOIN library_tracks t ON t.id = e.trackId
        WHERE e.localEpochDay >= :fromDay AND e.localEpochDay < :untilDay AND e.album IS NOT NULL AND TRIM(e.album) != ''
        GROUP BY COALESCE(NULLIF(t.albumKey, ''), LOWER(TRIM(e.album)) || char(31) || LOWER(TRIM(e.artist)))
        HAVING playCount > 0 ORDER BY playCount DESC, listenedMs DESC, rankKey LIMIT :limit
    """)
    suspend fun monthlyAlbums(fromDay: Long, untilDay: Long, limit: Int): List<PlayEventRankRow>

    @Query("DELETE FROM library_play_events")
    suspend fun clear()
}
