package app.echo.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LastFmLibraryDao {
    @Query("SELECT id, title, artist FROM visible_library_tracks WHERE source IN ('mediastore', 'saf') AND normalizedTitle IN (:titles) LIMIT 10001")
    suspend fun matchCandidates(titles: List<String>): List<LastFmLocalTrack>

    @Query("SELECT t.id, t.title, t.artist FROM visible_library_tracks t JOIN library_favorites f ON f.trackId = t.id WHERE t.source IN ('mediastore', 'saf') AND t.id > :after ORDER BY t.id LIMIT 200")
    suspend fun favoritePage(after: String): List<LastFmLocalTrack>

    @Query("SELECT COUNT(*) FROM library_favorites")
    fun favoriteChanges(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(favorite: LibraryFavoriteEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addStats(stats: LibraryPlaybackStatsEntity): Long

    @Query("UPDATE library_playback_stats SET playCount = :count WHERE trackId = :id AND playCount < :count")
    suspend fun raisePlayCount(id: String, count: Int): Int
}
