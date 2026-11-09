package app.echo.android.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import app.echo.android.model.library.AlbumSummary
import kotlinx.coroutines.flow.Flow

private const val LikedAlbumSummaries = """
    SELECT s.albumKey, s.title, s.albumArtist, s.artist, s.artworkUri,
           s.trackCount, s.durationMs, s.year, s.addedAtSeconds
    FROM library_liked_albums f JOIN library_album_summaries s ON s.albumKey = f.albumKey
    WHERE s.isRemote = 0
"""
private const val LikedAlbumOrder = " ORDER BY f.likedAtEpochMs DESC, f.albumKey"

@Entity(tableName = "library_liked_albums", indices = [Index("likedAtEpochMs")])
internal data class LibraryLikedAlbumEntity(
    @PrimaryKey val albumKey: String,
    val likedAtEpochMs: Long,
)

@Dao
internal interface LibraryLikedAlbumDao {
    @Query(LikedAlbumSummaries + """
        AND (:query IS NULL OR instr(lower(s.title), lower(:query)) > 0
            OR instr(lower(COALESCE(s.albumArtist, s.artist)), lower(:query)) > 0)
    """ + LikedAlbumOrder)
    fun pageLikedAlbums(query: String?): PagingSource<Int, AlbumSummary>

    @Query(LikedAlbumSummaries + LikedAlbumOrder + " LIMIT :limit")
    fun observeLikedAlbums(limit: Int): Flow<List<AlbumSummary>>

    @Query("""
        SELECT EXISTS(SELECT 1 FROM library_liked_albums f WHERE f.albumKey = s.albumKey)
        FROM library_album_summaries s WHERE s.albumKey = :albumKey AND s.isRemote = 0
    """)
    fun observeLiked(albumKey: String): Flow<Boolean?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun like(album: LibraryLikedAlbumEntity)

    @Query("DELETE FROM library_liked_albums WHERE albumKey = :albumKey")
    suspend fun unlike(albumKey: String)
}
