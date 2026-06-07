package app.echo.android.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface LibraryTrackDao {
    @Query(
        """
        SELECT * FROM library_tracks
        WHERE (:query IS NULL OR title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%')
        ORDER BY title COLLATE NOCASE ASC
        """,
    )
    fun pageTracks(query: String?): PagingSource<Int, LibraryTrackEntity>

    @Query("SELECT COUNT(*) FROM library_tracks")
    suspend fun countTracks(): Int

    @Upsert
    suspend fun upsertAll(tracks: List<LibraryTrackEntity>)

    @Query("DELETE FROM library_tracks WHERE source = :source")
    suspend fun deleteFromSource(source: String)

    @Transaction
    suspend fun replaceMediaStoreSnapshot(tracks: List<LibraryTrackEntity>) {
        deleteFromSource("mediastore")
        if (tracks.isNotEmpty()) upsertAll(tracks)
    }
}
