package app.echo.android.data

import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Independent user preference: scanning or clearing an index must not silently unhide a song. */
@Entity(tableName = "library_hidden_tracks", indices = [Index(value = ["hiddenAtEpochMs", "trackId"])])
data class LibraryHiddenTrackEntity(@PrimaryKey val trackId: String, val title: String, val artist: String,
    val album: String?, val hiddenAtEpochMs: Long)

@DatabaseView(value = VisibleLibraryTrackSql, viewName = "visible_library_tracks")
data class VisibleLibraryTrackView(@Embedded val track: LibraryTrackEntity)

internal const val VisibleLibraryTrackSql = "SELECT * FROM library_tracks WHERE id NOT IN (SELECT trackId FROM library_hidden_tracks)"

@Dao
interface LibraryHiddenTrackDao {
    @Query("SELECT * FROM library_hidden_tracks ORDER BY hiddenAtEpochMs DESC, trackId")
    fun page(): PagingSource<Int, LibraryHiddenTrackEntity>
    @Query("SELECT COUNT(*) FROM library_hidden_tracks")
    fun count(): Flow<Int>
    @Query("SELECT trackId FROM library_hidden_tracks ORDER BY trackId LIMIT 256")
    suspend fun firstBatch(): List<String>
    @Query("SELECT trackId FROM library_hidden_tracks WHERE trackId IN (:ids)")
    suspend fun hiddenIds(ids: List<String>): List<String>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun hide(rows: List<LibraryHiddenTrackEntity>)
    @Query("DELETE FROM library_hidden_tracks WHERE trackId IN (:ids)")
    suspend fun restore(ids: List<String>)
}
