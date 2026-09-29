package app.echo.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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

@Dao
interface LibraryPlayEventDao {
    @Insert
    suspend fun insert(event: LibraryPlayEventEntity): Long

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

    @Query("DELETE FROM library_play_events")
    suspend fun clear()
}
