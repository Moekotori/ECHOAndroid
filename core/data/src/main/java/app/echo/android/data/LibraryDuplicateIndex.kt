package app.echo.android.data

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "library_duplicate_control")
data class LibraryDuplicateControl(@PrimaryKey val id: Int = 0, val generation: String = "", val enabled: Boolean = false,
    val updatedAt: Long = 0, val processed: Int = 0, val skipped: Int = 0, val groupCount: Int = 0, val hiddenCount: Int = 0)

@Entity(tableName = "library_duplicate_members", primaryKeys = ["generation", "trackId"], indices = [
    Index(value = ["trackId"]), Index(value = ["generation", "groupKey"]),
])
data class LibraryDuplicateMember(val generation: String, val trackId: String, val groupKey: String, val versionKey: String,
    val representativeId: String, val snapshot: String, val rank: Int, val score: Int,
    val sampleRateHz: Int?, val bitDepth: Int?, val bitrate: Int?, val codecMime: String?)

data class LibraryDuplicateSummary(val enabled: Boolean, val updatedAt: Long, val processed: Int, val skipped: Int,
    val groups: Int, val hidden: Int)
data class LibraryDuplicateBucket(val title: String, val artist: String, val album: String, val albumIdentity: String, val start: Long, val end: Long)
data class LibraryDuplicateCandidate(@Embedded val track: LibraryTrackEntity, val duplicateSnapshot: String)
data class LibraryDuplicateCounts(val groups: Int, val hidden: Int)

@Dao
interface LibraryDuplicateDao {
    @Query("SELECT * FROM library_duplicate_control WHERE id=0") suspend fun control(): LibraryDuplicateControl?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveControl(value: LibraryDuplicateControl)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMembers(values: List<LibraryDuplicateMember>)
    @Query("DELETE FROM library_duplicate_members WHERE generation != :generation") suspend fun clearOthers(generation: String)
    @Query("DELETE FROM library_duplicate_members WHERE generation = :generation") suspend fun discard(generation: String)
    @Query("DELETE FROM library_duplicate_members WHERE generation=:generation AND trackId=:id") suspend fun removeMember(generation: String, id: String)
    @Query("SELECT * FROM library_duplicate_members WHERE generation=:generation AND trackId=:id LIMIT 1")
    suspend fun member(generation: String, id: String): LibraryDuplicateMember?
    @Query("SELECT * FROM library_duplicate_members WHERE generation=:generation AND groupKey=:groupKey ORDER BY rank,trackId LIMIT 256")
    suspend fun group(generation: String, groupKey: String): List<LibraryDuplicateMember>
    @Query("UPDATE library_duplicate_members SET representativeId=:representative WHERE generation=:generation AND groupKey=:groupKey")
    suspend fun setRepresentative(generation: String, groupKey: String, representative: String)
    @RawQuery suspend fun candidates(query: SupportSQLiteQuery): List<LibraryDuplicateCandidate>
    @RawQuery suspend fun members(query: SupportSQLiteQuery): List<LibraryDuplicateMember>
    @RawQuery suspend fun count(query: SupportSQLiteQuery): Int
    @Query("SELECT COUNT(DISTINCT groupKey) AS `groups`, COUNT(*)-COUNT(DISTINCT groupKey) AS hidden FROM library_duplicate_members WHERE generation=:generation")
    suspend fun counts(generation: String): LibraryDuplicateCounts
    @RawQuery(observedEntities = [LibraryDuplicateControl::class, LibraryTrackEntity::class, LibraryHiddenTrackEntity::class])
    fun representative(query: SupportSQLiteQuery): Flow<String?>

    @Query("""SELECT COALESCE((SELECT enabled FROM library_duplicate_control WHERE id=0),0) AS enabled,
        COALESCE((SELECT updatedAt FROM library_duplicate_control WHERE id=0),0) AS updatedAt,
        COALESCE((SELECT processed FROM library_duplicate_control WHERE id=0),0) AS processed,
        COALESCE((SELECT skipped FROM library_duplicate_control WHERE id=0),0) AS skipped,
        COALESCE((SELECT groupCount FROM library_duplicate_control WHERE id=0),0) AS `groups`,
        COALESCE((SELECT hiddenCount FROM library_duplicate_control WHERE id=0),0) AS hidden""")
    fun observeSummary(): Flow<LibraryDuplicateSummary>

    @Query("""SELECT normalizedTitle AS title,normalizedArtist AS artist,normalizedAlbum AS album,albumKey AS albumIdentity,
        clipStartMs AS `start`,clipEndMs AS `end` FROM visible_library_tracks
        WHERE source IN ('mediastore','saf') AND normalizedTitle IS NOT NULL AND normalizedTitle!=''
        AND normalizedArtist IS NOT NULL AND normalizedArtist!='' AND normalizedAlbum IS NOT NULL AND normalizedAlbum!='' AND albumKey!=''
        AND (normalizedTitle,normalizedArtist,normalizedAlbum,albumKey,clipStartMs,clipEndMs)>(:title,:artist,:album,:albumIdentity,:start,:end)
        GROUP BY normalizedTitle,normalizedArtist,normalizedAlbum,albumKey,clipStartMs,clipEndMs HAVING COUNT(*)>1
        ORDER BY normalizedTitle,normalizedArtist,normalizedAlbum,albumKey,clipStartMs,clipEndMs LIMIT 32""")
    suspend fun buckets(title: String, artist: String, album: String, albumIdentity: String, start: Long, end: Long): List<LibraryDuplicateBucket>
}
