package app.echo.android.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.ArtistSummary
import app.echo.android.model.library.LibraryStats
import kotlinx.coroutines.flow.Flow

data class TrackFingerprint(
    val id: String,
    val contentUri: String,
    val fingerprint: String?,
)

@Dao
interface LibraryTrackDao {
    @Query(
        """
        SELECT * FROM library_tracks
        ORDER BY title COLLATE NOCASE ASC
        """,
    )
    fun pageTracks(): PagingSource<Int, LibraryTrackEntity>

    @Query(
        """
        SELECT library_tracks.* FROM library_tracks
        JOIN library_tracks_fts ON library_tracks.id = library_tracks_fts.trackId
        WHERE library_tracks_fts MATCH :matchQuery
        ORDER BY
            CASE
                WHEN library_tracks.normalizedTitle LIKE :rankQuery THEN 0
                WHEN library_tracks.normalizedArtist LIKE :rankQuery THEN 1
                WHEN library_tracks.normalizedAlbum LIKE :rankQuery THEN 2
                ELSE 3
            END,
            library_tracks.title COLLATE NOCASE ASC
        """,
    )
    fun pageTracksByFts(matchQuery: String, rankQuery: String): PagingSource<Int, LibraryTrackEntity>

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'
        ORDER BY
            CASE
                WHEN normalizedTitle LIKE :rankQuery THEN 0
                WHEN normalizedArtist LIKE :rankQuery THEN 1
                WHEN normalizedAlbum LIKE :rankQuery THEN 2
                ELSE 3
            END,
            title COLLATE NOCASE ASC
        """,
    )
    fun pageTracksByLike(query: String, rankQuery: String): PagingSource<Int, LibraryTrackEntity>

    @Query("SELECT trackId FROM library_tracks_fts WHERE library_tracks_fts MATCH :matchQuery LIMIT 1")
    suspend fun validateFtsQuery(matchQuery: String): String?

    @Query("SELECT * FROM library_tracks WHERE id = :trackId LIMIT 1")
    suspend fun getTrackById(trackId: String): LibraryTrackEntity?

    @Query(
        """
        SELECT * FROM library_tracks
        ORDER BY title COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    suspend fun getTrackQueue(limit: Int): List<LibraryTrackEntity>

    @Query(
        """
        SELECT library_tracks.* FROM library_tracks
        JOIN library_tracks_fts ON library_tracks.id = library_tracks_fts.trackId
        WHERE library_tracks_fts MATCH :matchQuery
        ORDER BY
            CASE
                WHEN library_tracks.normalizedTitle LIKE :rankQuery THEN 0
                WHEN library_tracks.normalizedArtist LIKE :rankQuery THEN 1
                WHEN library_tracks.normalizedAlbum LIKE :rankQuery THEN 2
                ELSE 3
            END,
            library_tracks.title COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    suspend fun getTrackQueueByFts(
        matchQuery: String,
        rankQuery: String,
        limit: Int,
    ): List<LibraryTrackEntity>

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'
        ORDER BY
            CASE
                WHEN normalizedTitle LIKE :rankQuery THEN 0
                WHEN normalizedArtist LIKE :rankQuery THEN 1
                WHEN normalizedAlbum LIKE :rankQuery THEN 2
                ELSE 3
            END,
            title COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    suspend fun getTrackQueueByLike(
        query: String,
        rankQuery: String,
        limit: Int,
    ): List<LibraryTrackEntity>

    @Query(
        """
        SELECT * FROM library_tracks
        ORDER BY dateModifiedSeconds DESC, title COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    fun observeRecommendedTracks(limit: Int): Flow<List<LibraryTrackEntity>>

    @Query(
        """
        SELECT COUNT(*) AS trackCount,
               COUNT(DISTINCT (
                   COALESCE(NULLIF(normalizedAlbum, ''), '未知专辑') ||
                   '::' ||
                   COALESCE(NULLIF(normalizedAlbumArtist, ''), NULLIF(normalizedArtist, ''), '未知艺术家')
               )) AS albumCount,
               COUNT(DISTINCT COALESCE(NULLIF(normalizedArtist, ''), '未知艺术家')) AS artistCount,
               COALESCE(SUM(durationMs), 0) AS durationMs,
               COALESCE(SUM(sizeBytes), 0) AS totalSizeBytes
        FROM library_tracks
        """,
    )
    fun observeLibraryStats(): Flow<LibraryStats>

    @Query(
        """
        SELECT (
                   COALESCE(NULLIF(normalizedAlbum, ''), '未知专辑') ||
                   '::' ||
                   COALESCE(NULLIF(normalizedAlbumArtist, ''), NULLIF(normalizedArtist, ''), '未知艺术家')
               ) AS albumKey,
               CASE WHEN album IS NULL OR trim(album) = '' THEN '未知专辑' ELSE album END AS title,
               CASE
                   WHEN albumArtist IS NOT NULL AND trim(albumArtist) != '' THEN albumArtist
                   WHEN artist IS NOT NULL AND trim(artist) != '' THEN artist
                   ELSE NULL
               END AS albumArtist,
               CASE WHEN artist IS NULL OR trim(artist) = '' THEN NULL ELSE artist END AS artist,
               MAX(artworkUri) AS artworkUri,
               COUNT(*) AS trackCount,
               COALESCE(SUM(durationMs), 0) AS durationMs,
               MIN(CASE WHEN year IS NOT NULL AND year > 0 THEN year ELSE NULL END) AS year
        FROM library_tracks
        WHERE (:query IS NULL OR
               normalizedTitle LIKE '%' || lower(trim(:query)) || '%' OR
               normalizedArtist LIKE '%' || lower(trim(:query)) || '%' OR
               normalizedAlbum LIKE '%' || lower(trim(:query)) || '%' OR
               normalizedAlbumArtist LIKE '%' || lower(trim(:query)) || '%')
        GROUP BY albumKey
        ORDER BY
            CASE WHEN :sort = 'Artist' THEN albumArtist END COLLATE NOCASE ASC,
            CASE WHEN :sort = 'Year' THEN year END DESC,
            CASE WHEN :sort = 'TrackCount' THEN trackCount END DESC,
            CASE WHEN :sort = 'Duration' THEN durationMs END DESC,
            title COLLATE NOCASE ASC
        """,
    )
    fun pageAlbums(query: String?, sort: String): PagingSource<Int, AlbumSummary>

    @Query(
        """
        SELECT COALESCE(NULLIF(normalizedArtist, ''), '未知艺术家') AS artistKey,
               CASE WHEN artist IS NULL OR trim(artist) = '' THEN '未知艺术家' ELSE artist END AS name,
               MAX(artworkUri) AS artworkUri,
               COUNT(DISTINCT (
                   COALESCE(NULLIF(normalizedAlbum, ''), '未知专辑') ||
                   '::' ||
                   COALESCE(NULLIF(normalizedAlbumArtist, ''), NULLIF(normalizedArtist, ''), '未知艺术家')
               )) AS albumCount,
               COUNT(*) AS trackCount,
               COALESCE(SUM(durationMs), 0) AS durationMs
        FROM library_tracks
        WHERE (:query IS NULL OR
               normalizedArtist LIKE '%' || lower(trim(:query)) || '%' OR
               normalizedAlbumArtist LIKE '%' || lower(trim(:query)) || '%' OR
               normalizedTitle LIKE '%' || lower(trim(:query)) || '%' OR
               normalizedAlbum LIKE '%' || lower(trim(:query)) || '%')
        GROUP BY artistKey
        ORDER BY
            CASE WHEN :sort = 'AlbumCount' THEN albumCount END DESC,
            CASE WHEN :sort = 'TrackCount' THEN trackCount END DESC,
            CASE WHEN :sort = 'Duration' THEN durationMs END DESC,
            name COLLATE NOCASE ASC
        """,
    )
    fun pageArtists(query: String?, sort: String): PagingSource<Int, ArtistSummary>

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE (
            COALESCE(NULLIF(normalizedAlbum, ''), '未知专辑') ||
            '::' ||
            COALESCE(NULLIF(normalizedAlbumArtist, ''), NULLIF(normalizedArtist, ''), '未知艺术家')
        ) = :albumKey
        ORDER BY
            CASE WHEN discNumber IS NULL THEN 0 ELSE discNumber END ASC,
            CASE WHEN trackNumber IS NULL THEN 0 ELSE trackNumber END ASC,
            title COLLATE NOCASE ASC
        """,
    )
    fun pageTracksByAlbum(albumKey: String): PagingSource<Int, LibraryTrackEntity>

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE COALESCE(NULLIF(normalizedArtist, ''), '未知艺术家') = :artistKey
        ORDER BY
            album COLLATE NOCASE ASC,
            CASE WHEN discNumber IS NULL THEN 0 ELSE discNumber END ASC,
            CASE WHEN trackNumber IS NULL THEN 0 ELSE trackNumber END ASC,
            title COLLATE NOCASE ASC
        """,
    )
    fun pageTracksByArtist(artistKey: String): PagingSource<Int, LibraryTrackEntity>

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE (
            COALESCE(NULLIF(normalizedAlbum, ''), '未知专辑') ||
            '::' ||
            COALESCE(NULLIF(normalizedAlbumArtist, ''), NULLIF(normalizedArtist, ''), '未知艺术家')
        ) = :albumKey
        ORDER BY
            CASE WHEN discNumber IS NULL THEN 0 ELSE discNumber END ASC,
            CASE WHEN trackNumber IS NULL THEN 0 ELSE trackNumber END ASC,
            title COLLATE NOCASE ASC
        """,
    )
    suspend fun getTracksByAlbum(albumKey: String): List<LibraryTrackEntity>

    @RawQuery
    suspend fun getAlbumTracksForPlayback(query: SupportSQLiteQuery): List<LibraryTrackEntity>

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE COALESCE(NULLIF(normalizedArtist, ''), '未知艺术家') = :artistKey
        ORDER BY
            album COLLATE NOCASE ASC,
            CASE WHEN discNumber IS NULL THEN 0 ELSE discNumber END ASC,
            CASE WHEN trackNumber IS NULL THEN 0 ELSE trackNumber END ASC,
            title COLLATE NOCASE ASC
        """,
    )
    suspend fun getTracksByArtist(artistKey: String): List<LibraryTrackEntity>

    @RawQuery
    suspend fun getArtistTracksForPlayback(query: SupportSQLiteQuery): List<LibraryTrackEntity>

    @Query("SELECT COUNT(*) FROM library_tracks")
    suspend fun countTracks(): Int

    @Query("SELECT COUNT(*) FROM library_tracks WHERE source = :source")
    suspend fun countTracksFromSource(source: String): Int

    @Query(
        """
        SELECT id, contentUri, fingerprint FROM library_tracks
        WHERE source = :source
        """,
    )
    suspend fun getExistingMediaStoreFingerprints(source: String = "mediastore"): List<TrackFingerprint>

    @Query(
        """
        SELECT id, contentUri, fingerprint FROM library_tracks
        WHERE source = :source
          AND relativePath LIKE :relativePathLike ESCAPE '\'
        """,
    )
    suspend fun getExistingMediaStoreFingerprintsInRelativePath(
        source: String,
        relativePathLike: String,
    ): List<TrackFingerprint>

    @Upsert
    suspend fun upsertBatch(tracks: List<LibraryTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFtsBatch(tracks: List<LibraryTrackFtsEntity>)

    @Query("DELETE FROM library_tracks_fts WHERE trackId IN (:trackIds)")
    suspend fun deleteFtsByTrackIds(trackIds: List<String>): Int

    @Query("DELETE FROM library_tracks_fts")
    suspend fun clearFts()

    @Query("SELECT * FROM library_tracks")
    suspend fun getAllTracksForFtsRebuild(): List<LibraryTrackEntity>

    @Transaction
    suspend fun upsertBatchWithFts(tracks: List<LibraryTrackEntity>) {
        if (tracks.isEmpty()) return
        upsertBatch(tracks)
        upsertFtsBatch(tracks)
    }

    @Transaction
    suspend fun upsertFtsBatch(tracks: List<LibraryTrackEntity>) {
        if (tracks.isEmpty()) return
        tracks.map(LibraryTrackEntity::id)
            .chunked(500)
            .forEach { trackIds -> deleteFtsByTrackIds(trackIds) }
        tracks.map(LibraryTrackEntity::toFtsEntity)
            .chunked(500)
            .forEach { ftsTracks -> insertFtsBatch(ftsTracks) }
    }

    @Transaction
    suspend fun rebuildFts() {
        clearFts()
        getAllTracksForFtsRebuild()
            .chunked(500)
            .forEach { tracks -> insertFtsBatch(tracks.map(LibraryTrackEntity::toFtsEntity)) }
    }

    @Query("UPDATE library_tracks SET lastSeenScanRunId = :scanRunId WHERE id IN (:ids)")
    suspend fun markSeen(ids: List<String>, scanRunId: Long): Int

    @Query("SELECT id FROM library_tracks WHERE source = :source AND lastSeenScanRunId != :scanRunId")
    suspend fun getMissingTrackIdsFromSource(source: String, scanRunId: Long): List<String>

    @Query("DELETE FROM library_tracks WHERE source = :source AND lastSeenScanRunId != :scanRunId")
    suspend fun deleteMissingFromSource(source: String, scanRunId: Long): Int

    @Query(
        """
        SELECT id FROM library_tracks
        WHERE source = :source
          AND relativePath LIKE :relativePathLike ESCAPE '\'
          AND lastSeenScanRunId != :scanRunId
        """,
    )
    suspend fun getMissingTrackIdsFromRelativePath(
        source: String,
        relativePathLike: String,
        scanRunId: Long,
    ): List<String>

    @Query(
        """
        DELETE FROM library_tracks
        WHERE source = :source
          AND relativePath LIKE :relativePathLike ESCAPE '\'
          AND lastSeenScanRunId != :scanRunId
        """,
    )
    suspend fun deleteMissingFromRelativePath(
        source: String,
        relativePathLike: String,
        scanRunId: Long,
    ): Int
}
