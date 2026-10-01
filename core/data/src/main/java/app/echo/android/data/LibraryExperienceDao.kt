package app.echo.android.data

import androidx.paging.PagingSource
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

internal const val SmartRuleMatchSql = """
 t.source IN ('mediastore', 'saf')
 AND (r.excludeText = '' OR instr(lower(t.title || ' ' || t.artist || ' ' || COALESCE(t.album,'')), lower(r.excludeText)) = 0)
 AND (
 (r.matchAny = 0
  AND (r.artist = '' OR instr(lower(t.artist), lower(r.artist)) > 0)
  AND (r.genre = '' OR instr(lower(COALESCE(t.genre,'')), lower(r.genre)) > 0)
  AND (r.album = '' OR instr(lower(COALESCE(t.album,'')), lower(r.album)) > 0)
  AND (r.folder = '' OR instr(lower(COALESCE(t.relativePath,'')), lower(r.folder)) > 0)
  AND (r.format = '' OR instr(lower(COALESCE(t.mimeType,'') || ' ' || COALESCE(t.fileName,'') || ' ' || t.contentUri), lower(r.format)) > 0)
  AND (r.favoriteOnly = 0 OR EXISTS(SELECT 1 FROM library_favorites f WHERE f.trackId = t.id))
  AND (r.minimumYear = 0 OR t.year >= r.minimumYear)
  AND (r.maximumYear = 0 OR t.year <= r.maximumYear)
  AND (r.minimumDurationSeconds = 0 OR t.durationMs >= r.minimumDurationSeconds * 1000)
  AND (r.maximumDurationSeconds = 0 OR t.durationMs <= r.maximumDurationSeconds * 1000)
  AND (r.notPlayedDays = 0 OR COALESCE(s.lastPlayedAtEpochMs,0) = 0 OR s.lastPlayedAtEpochMs < (CAST(strftime('%s','now') AS INTEGER) - r.notPlayedDays * 86400) * 1000))
 OR (r.matchAny = 1 AND (
   (r.artist = '' AND r.genre = '' AND r.album = '' AND r.folder = '' AND r.format = '' AND r.favoriteOnly = 0 AND r.minimumYear = 0 AND r.maximumYear = 0 AND r.minimumDurationSeconds = 0 AND r.maximumDurationSeconds = 0 AND r.notPlayedDays = 0)
   OR (r.artist != '' AND instr(lower(t.artist), lower(r.artist)) > 0)
   OR (r.genre != '' AND instr(lower(COALESCE(t.genre,'')), lower(r.genre)) > 0)
   OR (r.album != '' AND instr(lower(COALESCE(t.album,'')), lower(r.album)) > 0)
   OR (r.folder != '' AND instr(lower(COALESCE(t.relativePath,'')), lower(r.folder)) > 0)
   OR (r.format != '' AND instr(lower(COALESCE(t.mimeType,'') || ' ' || COALESCE(t.fileName,'') || ' ' || t.contentUri), lower(r.format)) > 0)
   OR (r.favoriteOnly = 1 AND EXISTS(SELECT 1 FROM library_favorites f WHERE f.trackId = t.id))
   OR ((r.minimumYear > 0 OR r.maximumYear > 0) AND (r.minimumYear = 0 OR t.year >= r.minimumYear) AND (r.maximumYear = 0 OR t.year <= r.maximumYear))
   OR ((r.minimumDurationSeconds > 0 OR r.maximumDurationSeconds > 0) AND (r.minimumDurationSeconds = 0 OR t.durationMs >= r.minimumDurationSeconds * 1000) AND (r.maximumDurationSeconds = 0 OR t.durationMs <= r.maximumDurationSeconds * 1000))
   OR (r.notPlayedDays > 0 AND (COALESCE(s.lastPlayedAtEpochMs,0) = 0 OR s.lastPlayedAtEpochMs < (CAST(strftime('%s','now') AS INTEGER) - r.notPlayedDays * 86400) * 1000))
 )))
"""

@Dao
interface LibraryExperienceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRule(rule: LibrarySmartRuleEntity)

    @Query("SELECT * FROM library_smart_rules WHERE playlistId = :id")
    suspend fun rule(id: String): LibrarySmartRuleEntity?

    @Query("SELECT playlistId FROM library_smart_rules WHERE pinned = 1")
    fun observeHomePins(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM library_smart_rules WHERE pinned = 1")
    suspend fun homePinCount(): Int

    @Query("UPDATE library_smart_rules SET pinned = :pinned WHERE playlistId = :id")
    suspend fun setHomePin(id: String, pinned: Boolean)

    @Query("DELETE FROM library_smart_rules WHERE playlistId = :id")
    suspend fun deleteRule(id: String)

    @Query("SELECT p.id, p.name, p.source, (SELECT COUNT(*) FROM library_tracks t " +
        "LEFT JOIN library_playback_stats s ON s.trackId = t.id WHERE " + SmartRuleMatchSql +
        ") AS trackCount, p.artworkUri, p.updatedAtEpochMs FROM library_smart_rules r " +
        "JOIN library_playlists p ON p.id = r.playlistId")
    fun observeSmartPlaylists(): Flow<List<PlaylistSummaryRow>>

    @Query("SELECT p.id, p.name, p.source, (SELECT COUNT(*) FROM library_tracks t " +
        "LEFT JOIN library_playback_stats s ON s.trackId = t.id WHERE " + SmartRuleMatchSql +
        ") AS trackCount, p.artworkUri, p.updatedAtEpochMs FROM library_smart_rules r " +
        "JOIN library_playlists p ON p.id = r.playlistId WHERE r.playlistId = :id")
    suspend fun smartPlaylist(id: String): PlaylistSummaryRow?

    @RawQuery(observedEntities = [LibraryTrackEntity::class, LibrarySmartRuleEntity::class,
        LibraryFavoriteEntity::class, LibraryPlaybackStatsEntity::class])
    fun pageRuleTracks(query: SupportSQLiteQuery): PagingSource<Int, LibraryTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBookmark(bookmark: LibraryBookmarkEntity)

    @Query("SELECT * FROM library_bookmarks WHERE trackId = :trackId ORDER BY positionMs, id")
    fun bookmarks(trackId: String): Flow<List<LibraryBookmarkEntity>>

    @RawQuery(observedEntities = [LibraryBookmarkEntity::class, LibraryTrackEntity::class])
    fun pageMoments(query: SupportSQLiteQuery): PagingSource<Int, LibraryMomentRow>

    @RawQuery
    suspend fun queryMoments(query: SupportSQLiteQuery): List<LibraryMomentRow>

    @Query("DELETE FROM library_bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: String)

    @Query("SELECT * FROM library_bookmarks ORDER BY trackId, positionMs LIMIT 10001")
    suspend fun exportBookmarks(): List<LibraryBookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun archive(row: LibraryRepairArchiveEntity)

    @Query("SELECT * FROM library_repair_archive WHERE id = :id")
    suspend fun archived(id: String): LibraryRepairArchiveEntity?

    @Query("SELECT * FROM library_repair_archive ORDER BY archivedAt DESC, id LIMIT :limit")
    suspend fun archives(limit: Int): List<LibraryRepairArchiveEntity>

    @Query("DELETE FROM library_repair_archive WHERE id = :id")
    suspend fun deleteArchive(id: String)
}
