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
import app.echo.android.model.library.FolderSummary
import app.echo.android.model.library.LibraryStats
import kotlinx.coroutines.flow.Flow

data class TrackFingerprint(
    val id: String,
    val contentUri: String,
    val sampleRateHz: Int?,
    val fingerprint: String?,
    val sizeBytes: Long = 0L,
    val dateModifiedSeconds: Long = 0L,
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

    @Query("SELECT * FROM library_tracks WHERE contentUri = :contentUri LIMIT 1")
    suspend fun getTrackByContentUri(contentUri: String): LibraryTrackEntity?

    @Query("SELECT * FROM library_tracks WHERE source = :source AND metadataEditedAtEpochMs IS NOT NULL")
    suspend fun getMetadataEditedTracks(source: String): List<LibraryTrackEntity>

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE source = :source
          AND relativePath LIKE :relativePathLike ESCAPE '\'
          AND metadataEditedAtEpochMs IS NOT NULL
        """,
    )
    suspend fun getMetadataEditedTracksInRelativePath(
        source: String,
        relativePathLike: String,
    ): List<LibraryTrackEntity>

    @RawQuery(
        observedEntities = [
            LibraryTrackEntity::class,
            LibraryTrackFtsEntity::class,
            LibraryPlaybackStatsEntity::class,
        ],
    )
    fun pageTracksSorted(query: SupportSQLiteQuery): PagingSource<Int, LibraryTrackEntity>

    @RawQuery
    suspend fun queryTracks(query: SupportSQLiteQuery): List<LibraryTrackEntity>

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE (source = 'mediastore' OR source = 'saf')
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
        WHERE (source = 'mediastore' OR source = 'saf')
        ORDER BY dateModifiedSeconds DESC, title COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    fun observeRecommendedTracks(limit: Int): Flow<List<LibraryTrackEntity>>

    @Query(
        """
        SELECT
            COALESCE(SUM(trackCount), 0) AS trackCount,
            (SELECT COUNT(*) FROM library_album_summaries WHERE isRemote = 0) AS albumCount,
            (SELECT COUNT(*) FROM library_artist_summaries) AS artistCount,
            COALESCE(SUM(durationMs), 0) AS durationMs,
            COALESCE(SUM(totalSizeBytes), 0) AS totalSizeBytes
        FROM library_folder_summaries
        """,
    )
    fun observeLibraryStats(): Flow<LibraryStats>

    @Query(
        """
        SELECT albumKey, title, albumArtist, artist, artworkUri, trackCount, durationMs, year, addedAtSeconds
        FROM library_album_summaries
        WHERE isRemote = 0
          AND (:query IS NULL OR
               title LIKE '%' || :query || '%' OR
               albumArtist LIKE '%' || :query || '%' OR
               artist LIKE '%' || :query || '%' OR
               pinyinTitle LIKE '%' || lower(:query) || '%' OR
               pinyinArtist LIKE '%' || lower(:query) || '%')
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
        SELECT albumKey, title, albumArtist, artist, artworkUri, trackCount, durationMs, year, addedAtSeconds
        FROM library_album_summaries
        WHERE isRemote = 1
          AND (:query IS NULL OR
               title LIKE '%' || :query || '%' OR
               albumArtist LIKE '%' || :query || '%' OR
               artist LIKE '%' || :query || '%' OR
               pinyinTitle LIKE '%' || lower(:query) || '%' OR
               pinyinArtist LIKE '%' || lower(:query) || '%')
        ORDER BY
            CASE WHEN :sort = 'Artist' THEN albumArtist END COLLATE NOCASE ASC,
            CASE WHEN :sort = 'Year' THEN year END DESC,
            CASE WHEN :sort = 'TrackCount' THEN trackCount END DESC,
            CASE WHEN :sort = 'Duration' THEN durationMs END DESC,
            title COLLATE NOCASE ASC
        """,
    )
    fun pageRemoteAlbums(query: String?, sort: String): PagingSource<Int, AlbumSummary>

    @Query(
        """
        SELECT albumKey, title, albumArtist, artist, artworkUri, trackCount, durationMs, year, addedAtSeconds
        FROM library_album_summaries
        WHERE isRemote = 0
        ORDER BY addedAtSeconds DESC, title COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    fun observeRecentlyAddedAlbums(limit: Int): Flow<List<AlbumSummary>>

    @Query(
        """
        SELECT
            s.albumKey AS albumKey,
            s.title AS title,
            s.albumArtist AS albumArtist,
            s.artist AS artist,
            s.artworkUri AS artworkUri,
            s.trackCount AS trackCount,
            s.durationMs AS durationMs,
            s.year AS year,
            s.addedAtSeconds AS addedAtSeconds,
            COALESCE(SUM(stats.playCount), 0) AS playCount,
            COALESCE(MAX(stats.lastPlayedAtEpochMs), 0) AS lastPlayedAtEpochMs,
            COALESCE(MAX(f.favoritedAtEpochMs), 0) AS favoritedAtEpochMs
        FROM library_album_summaries s
        INNER JOIN library_tracks t ON t.albumKey = s.albumKey
        LEFT JOIN library_playback_stats stats ON stats.trackId = t.id
        LEFT JOIN library_favorites f ON f.trackId = t.id
        WHERE s.isRemote = 0
          AND (t.source = 'mediastore' OR t.source = 'saf')
        GROUP BY s.albumKey
        """,
    )
    fun observeAlbumListenStats(): Flow<List<LibraryAlbumListenStatsRow>>

    @Query(
        """
        SELECT artistKey, name, artworkUri, albumCount, trackCount, durationMs
        FROM library_artist_summaries
        WHERE (:query IS NULL OR
               name LIKE '%' || :query || '%' OR
               pinyinName LIKE '%' || lower(:query) || '%')
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
        SELECT folderKey, path, artworkUri, trackCount, albumCount, artistCount,
               durationMs, totalSizeBytes, latestModifiedSeconds
        FROM library_folder_summaries
        WHERE (:query IS NULL OR
               path LIKE '%' || :query || '%' OR
               folderKey LIKE '%' || :query || '%')
        ORDER BY
            CASE WHEN folderKey = '' THEN 1 ELSE 0 END,
            path COLLATE NOCASE ASC
        """,
    )
    fun pageFolders(query: String?): PagingSource<Int, FolderSummary>

    @Query(
        """
        SELECT albumKey, title, albumArtist, artist, artworkUri, trackCount, durationMs, year, addedAtSeconds
        FROM library_album_summaries
        WHERE albumKey = :albumKey
        LIMIT 1
        """,
    )
    suspend fun getAlbumSummary(albumKey: String): AlbumSummary?

    @Query(
        """
        SELECT albumKey, title, albumArtist, artist, artworkUri, trackCount, durationMs, year, addedAtSeconds
        FROM library_album_summaries
        WHERE albumKey = 'remote||' || :source || '||' || :albumKey
        LIMIT 1
        """,
    )
    suspend fun getRemoteAlbumSummary(source: String, albumKey: String): AlbumSummary?

    @Query(
        """
        SELECT artistKey, name, artworkUri, albumCount, trackCount, durationMs
        FROM library_artist_summaries
        WHERE artistKey = :artistKey
        LIMIT 1
        """,
    )
    suspend fun getArtistSummary(artistKey: String): ArtistSummary?

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE (source = 'mediastore' OR source = 'saf')
          AND albumKey = :albumKey
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
        WHERE source = :source
          AND albumKey = :albumKey
        ORDER BY
            CASE WHEN discNumber IS NULL THEN 0 ELSE discNumber END ASC,
            CASE WHEN trackNumber IS NULL THEN 0 ELSE trackNumber END ASC,
            title COLLATE NOCASE ASC
        """,
    )
    fun pageTracksByRemoteAlbum(source: String, albumKey: String): PagingSource<Int, LibraryTrackEntity>

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE (source = 'mediastore' OR source = 'saf')
          AND artistKey = :artistKey
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
        WHERE (source = 'mediastore' OR source = 'saf')
          AND (
            (:folderKey = '' AND (relativePath IS NULL OR trim(relativePath) = ''))
            OR relativePath = :folderKey
          )
        ORDER BY
            album COLLATE NOCASE ASC,
            CASE WHEN discNumber IS NULL THEN 0 ELSE discNumber END ASC,
            CASE WHEN trackNumber IS NULL THEN 0 ELSE trackNumber END ASC,
            title COLLATE NOCASE ASC
        """,
    )
    fun pageTracksByFolder(folderKey: String): PagingSource<Int, LibraryTrackEntity>

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE (source = 'mediastore' OR source = 'saf')
          AND albumKey = :albumKey
        ORDER BY
            CASE WHEN discNumber IS NULL THEN 0 ELSE discNumber END ASC,
            CASE WHEN trackNumber IS NULL THEN 0 ELSE trackNumber END ASC,
            title COLLATE NOCASE ASC
        """,
    )
    suspend fun getTracksByAlbum(albumKey: String): List<LibraryTrackEntity>

    @Query(
        """
        SELECT albumKey, title, albumArtist, artist, artworkUri, trackCount, durationMs, year, addedAtSeconds
        FROM library_album_summaries
        WHERE isRemote = 0
        ORDER BY title COLLATE NOCASE ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listAlbumsForBrowse(limit: Int, offset: Int): List<AlbumSummary>

    @Query(
        """
        SELECT artistKey, name, artworkUri, albumCount, trackCount, durationMs
        FROM library_artist_summaries
        ORDER BY name COLLATE NOCASE ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listArtistsForBrowse(limit: Int, offset: Int): List<ArtistSummary>

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE (source = 'mediastore' OR source = 'saf')
        ORDER BY dateModifiedSeconds DESC, title COLLATE NOCASE ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listRecentTracksForBrowse(limit: Int, offset: Int): List<LibraryTrackEntity>

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE (source = 'mediastore' OR source = 'saf')
          AND albumKey = :albumKey
        ORDER BY
            CASE WHEN discNumber IS NULL THEN 0 ELSE discNumber END ASC,
            CASE WHEN trackNumber IS NULL THEN 0 ELSE trackNumber END ASC,
            title COLLATE NOCASE ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listTracksByAlbumForBrowse(
        albumKey: String,
        limit: Int,
        offset: Int,
    ): List<LibraryTrackEntity>

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE (source = 'mediastore' OR source = 'saf')
          AND artistKey = :artistKey
        ORDER BY
            album COLLATE NOCASE ASC,
            CASE WHEN discNumber IS NULL THEN 0 ELSE discNumber END ASC,
            CASE WHEN trackNumber IS NULL THEN 0 ELSE trackNumber END ASC,
            title COLLATE NOCASE ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listTracksByArtistForBrowse(
        artistKey: String,
        limit: Int,
        offset: Int,
    ): List<LibraryTrackEntity>

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE source = :source
          AND albumKey = :albumKey
        ORDER BY
            CASE WHEN discNumber IS NULL THEN 0 ELSE discNumber END ASC,
            CASE WHEN trackNumber IS NULL THEN 0 ELSE trackNumber END ASC,
            title COLLATE NOCASE ASC
        """,
    )
    suspend fun getTracksByRemoteAlbum(source: String, albumKey: String): List<LibraryTrackEntity>

    @RawQuery
    suspend fun getAlbumTracksForPlayback(query: SupportSQLiteQuery): List<LibraryTrackEntity>

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE (source = 'mediastore' OR source = 'saf')
          AND artistKey = :artistKey
        ORDER BY
            album COLLATE NOCASE ASC,
            CASE WHEN discNumber IS NULL THEN 0 ELSE discNumber END ASC,
            CASE WHEN trackNumber IS NULL THEN 0 ELSE trackNumber END ASC,
            title COLLATE NOCASE ASC
        """,
    )
    suspend fun getTracksByArtist(artistKey: String): List<LibraryTrackEntity>

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE (source = 'mediastore' OR source = 'saf')
          AND (
            (:folderKey = '' AND (relativePath IS NULL OR trim(relativePath) = ''))
            OR relativePath = :folderKey
          )
        ORDER BY
            album COLLATE NOCASE ASC,
            CASE WHEN discNumber IS NULL THEN 0 ELSE discNumber END ASC,
            CASE WHEN trackNumber IS NULL THEN 0 ELSE trackNumber END ASC,
            title COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    suspend fun getTracksByFolderForPlayback(folderKey: String, limit: Int): List<LibraryTrackEntity>

    @RawQuery
    suspend fun getArtistTracksForPlayback(query: SupportSQLiteQuery): List<LibraryTrackEntity>

    @Query("SELECT COUNT(*) FROM library_tracks")
    suspend fun countTracks(): Int

    @Query("SELECT COUNT(*) FROM library_tracks WHERE source = :source")
    suspend fun countTracksFromSource(source: String): Int

    @Query(
        """
        SELECT id, contentUri, sampleRateHz, fingerprint, sizeBytes, dateModifiedSeconds FROM library_tracks
        WHERE source = :source
        """,
    )
    suspend fun getExistingMediaStoreFingerprints(source: String = "mediastore"): List<TrackFingerprint>

    @Query(
        """
        SELECT id, contentUri, sampleRateHz, fingerprint, sizeBytes, dateModifiedSeconds FROM library_tracks
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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaybackStats(stats: LibraryPlaybackStatsEntity): Long

    @Query(
        """
        UPDATE library_playback_stats
        SET playCount = playCount + 1,
            lastPlayedAtEpochMs = :playedAtEpochMs
        WHERE trackId = :trackId
        """,
    )
    suspend fun incrementPlaybackStats(trackId: String, playedAtEpochMs: Long): Int

    @Transaction
    suspend fun recordPlayback(trackId: String, playedAtEpochMs: Long) {
        val inserted = insertPlaybackStats(
            LibraryPlaybackStatsEntity(
                trackId = trackId,
                playCount = 1,
                lastPlayedAtEpochMs = playedAtEpochMs,
            ),
        )
        if (inserted == -1L) {
            incrementPlaybackStats(trackId, playedAtEpochMs)
        }
    }

    @Query("DELETE FROM library_tracks_fts WHERE trackId IN (:trackIds)")
    suspend fun deleteFtsByTrackIds(trackIds: List<String>): Int

    @Query("DELETE FROM library_tracks_fts")
    suspend fun clearFts()

    @Query("SELECT * FROM library_tracks")
    suspend fun getAllTracksForFtsRebuild(): List<LibraryTrackEntity>

    @Query(
        """
        SELECT library_tracks.id FROM library_tracks
        LEFT JOIN library_tracks_fts ON library_tracks.id = library_tracks_fts.trackId
        WHERE library_tracks_fts.trackId IS NULL
           OR (pinyinTitle IS NOT NULL AND trim(pinyinTitle) != '' AND instr(library_tracks_fts.normalizedText, pinyinTitle) = 0)
           OR (pinyinArtist IS NOT NULL AND trim(pinyinArtist) != '' AND instr(library_tracks_fts.normalizedText, pinyinArtist) = 0)
           OR (pinyinAlbum IS NOT NULL AND trim(pinyinAlbum) != '' AND instr(library_tracks_fts.normalizedText, pinyinAlbum) = 0)
        LIMIT 1
        """,
    )
    suspend fun findTrackWithStaleFtsSearchData(): String?

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE (title GLOB '*[^ -~]*' AND (pinyinTitle IS NULL OR pinyinTitle = normalizedTitle))
           OR (artist GLOB '*[^ -~]*' AND (pinyinArtist IS NULL OR pinyinArtist = normalizedArtist))
           OR (album IS NOT NULL AND album GLOB '*[^ -~]*' AND (pinyinAlbum IS NULL OR pinyinAlbum = normalizedAlbum))
        LIMIT :limit
        """,
    )
    suspend fun getTracksNeedingPinyinBackfill(limit: Int): List<LibraryTrackEntity>

    @Query(
        """
        SELECT * FROM library_tracks
        WHERE (source = 'mediastore' OR source = 'saf')
          AND (
            normalizedTitle LIKE '%' || lower(trim(:query)) || '%'
            OR normalizedArtist LIKE '%' || lower(trim(:query)) || '%'
            OR normalizedAlbum LIKE '%' || lower(trim(:query)) || '%'
            OR normalizedAlbumArtist LIKE '%' || lower(trim(:query)) || '%'
            OR pinyinTitle LIKE '%' || lower(trim(:query)) || '%'
            OR pinyinArtist LIKE '%' || lower(trim(:query)) || '%'
            OR pinyinAlbum LIKE '%' || lower(trim(:query)) || '%'
          )
        ORDER BY
            CASE
                WHEN normalizedTitle LIKE lower(trim(:query)) || '%' THEN 0
                WHEN pinyinTitle LIKE lower(trim(:query)) || '%' THEN 1
                WHEN normalizedArtist LIKE lower(trim(:query)) || '%' THEN 2
                WHEN pinyinArtist LIKE lower(trim(:query)) || '%' THEN 3
                WHEN normalizedAlbum LIKE lower(trim(:query)) || '%' THEN 4
                WHEN pinyinAlbum LIKE lower(trim(:query)) || '%' THEN 5
                ELSE 6
            END,
            title COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    suspend fun searchTracks(query: String, limit: Int): List<LibraryTrackEntity>

    @Query(
        """
        SELECT library_tracks.* FROM library_tracks
        JOIN library_tracks_fts ON library_tracks.id = library_tracks_fts.trackId
        WHERE library_tracks_fts MATCH :matchQuery
        ORDER BY
            CASE
                WHEN library_tracks.normalizedTitle LIKE :rankQuery THEN 0
                WHEN library_tracks.pinyinTitle LIKE :rankQuery THEN 1
                WHEN library_tracks.normalizedArtist LIKE :rankQuery THEN 2
                WHEN library_tracks.pinyinArtist LIKE :rankQuery THEN 3
                WHEN library_tracks.normalizedAlbum LIKE :rankQuery THEN 4
                ELSE 5
            END,
            library_tracks.title COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    suspend fun searchTracksByFts(matchQuery: String, rankQuery: String, limit: Int): List<LibraryTrackEntity>

    @Query(
        """
        SELECT albumKey, title, albumArtist, artist, artworkUri, trackCount, durationMs, year, addedAtSeconds
        FROM library_album_summaries
        WHERE isRemote = 0
          AND (
            title LIKE '%' || :query || '%'
            OR albumArtist LIKE '%' || :query || '%'
            OR artist LIKE '%' || :query || '%'
            OR pinyinTitle LIKE '%' || lower(:query) || '%'
            OR pinyinArtist LIKE '%' || lower(:query) || '%'
            OR EXISTS (
                SELECT 1
                FROM library_tracks AS matched_track
                WHERE (matched_track.source = 'mediastore' OR matched_track.source = 'saf')
                  AND matched_track.albumKey = library_album_summaries.albumKey
                  AND (
                    matched_track.normalizedTitle LIKE '%' || lower(:query) || '%'
                    OR matched_track.normalizedArtist LIKE '%' || lower(:query) || '%'
                    OR matched_track.normalizedAlbum LIKE '%' || lower(:query) || '%'
                    OR matched_track.normalizedAlbumArtist LIKE '%' || lower(:query) || '%'
                    OR matched_track.pinyinTitle LIKE '%' || lower(:query) || '%'
                    OR matched_track.pinyinArtist LIKE '%' || lower(:query) || '%'
                    OR matched_track.pinyinAlbum LIKE '%' || lower(:query) || '%'
                  )
            )
          )
        ORDER BY
            CASE
                WHEN title LIKE :query || '%' THEN 0
                WHEN pinyinTitle LIKE lower(:query) || '%' THEN 1
                WHEN albumArtist LIKE :query || '%' THEN 2
                WHEN pinyinArtist LIKE lower(:query) || '%' THEN 3
                ELSE 4
            END,
            title COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    suspend fun searchAlbums(query: String, limit: Int): List<AlbumSummary>

    @Query(
        """
        SELECT artistKey, name, artworkUri, albumCount, trackCount, durationMs
        FROM library_artist_summaries
        WHERE name LIKE '%' || :query || '%'
           OR pinyinName LIKE '%' || lower(:query) || '%'
           OR EXISTS (
                SELECT 1
                FROM library_tracks AS matched_track
                WHERE (matched_track.source = 'mediastore' OR matched_track.source = 'saf')
                  AND matched_track.artistKey = library_artist_summaries.artistKey
                  AND (
                    matched_track.normalizedTitle LIKE '%' || lower(:query) || '%'
                    OR matched_track.normalizedArtist LIKE '%' || lower(:query) || '%'
                    OR matched_track.normalizedAlbum LIKE '%' || lower(:query) || '%'
                    OR matched_track.normalizedAlbumArtist LIKE '%' || lower(:query) || '%'
                    OR matched_track.pinyinTitle LIKE '%' || lower(:query) || '%'
                    OR matched_track.pinyinArtist LIKE '%' || lower(:query) || '%'
                    OR matched_track.pinyinAlbum LIKE '%' || lower(:query) || '%'
                  )
           )
        ORDER BY
            CASE
                WHEN name LIKE :query || '%' THEN 0
                WHEN pinyinName LIKE lower(:query) || '%' THEN 1
                ELSE 2
            END,
            name COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    suspend fun searchArtists(query: String, limit: Int): List<ArtistSummary>

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

    @Query("SELECT id FROM library_tracks WHERE source = :source")
    suspend fun getIdsFromSource(source: String): List<String>

    @Query(
        """
        SELECT id FROM library_tracks
        WHERE source = :source
          AND relativePath LIKE :relativePathLike ESCAPE '\'
        """,
    )
    suspend fun getIdsFromRelativePath(source: String, relativePathLike: String): List<String>

    @Query("DELETE FROM library_album_summaries")
    suspend fun clearAlbumSummaries()

    @Query("DELETE FROM library_artist_summaries")
    suspend fun clearArtistSummaries()

    @Query("DELETE FROM library_folder_summaries")
    suspend fun clearFolderSummaries()

    @Query(EchoLibraryDatabase.RebuildAlbumSummariesSql)
    suspend fun insertAlbumSummariesFromTracks()

    @Query(EchoLibraryDatabase.RebuildArtistSummariesSql)
    suspend fun insertArtistSummariesFromTracks()

    @Query(EchoLibraryDatabase.RebuildFolderSummariesSql)
    suspend fun insertFolderSummariesFromTracks()

    @Query("SELECT COUNT(*) FROM library_album_summaries")
    suspend fun countAlbumSummaries(): Int

    @Query(
        """
        SELECT albumKey, artistKey, relativePath, source
        FROM library_tracks
        WHERE id IN (:ids)
        """,
    )
    suspend fun getSummaryKeyRows(ids: List<String>): List<TrackSummaryKeyRow>

    @Query("DELETE FROM library_album_summaries WHERE albumKey IN (:keys)")
    suspend fun deleteAlbumSummariesByKeys(keys: List<String>): Int

    @Query("DELETE FROM library_artist_summaries WHERE artistKey IN (:keys)")
    suspend fun deleteArtistSummariesByKeys(keys: List<String>): Int

    @Query("DELETE FROM library_folder_summaries WHERE folderKey IN (:keys)")
    suspend fun deleteFolderSummariesByKeys(keys: List<String>): Int

    @Query(EchoLibraryDatabase.RebuildAlbumSummariesForKeysSql)
    suspend fun insertAlbumSummariesForKeys(keys: List<String>)

    @Query(EchoLibraryDatabase.RebuildArtistSummariesForKeysSql)
    suspend fun insertArtistSummariesForKeys(keys: List<String>)

    @Query(EchoLibraryDatabase.RebuildFolderSummariesForKeysSql)
    suspend fun insertFolderSummariesForKeys(keys: List<String>)

    @Transaction
    suspend fun rebuildLibrarySummaries() {
        clearAlbumSummaries()
        clearArtistSummaries()
        clearFolderSummaries()
        insertAlbumSummariesFromTracks()
        insertArtistSummariesFromTracks()
        insertFolderSummariesFromTracks()
    }

    @Transaction
    suspend fun rebuildLibrarySummariesForKeys(
        albumKeys: Collection<String>,
        artistKeys: Collection<String>,
        folderKeys: Collection<String>,
    ) {
        albumKeys.distinct().chunked(SUMMARY_KEY_BATCH_SIZE).forEach { chunk ->
            deleteAlbumSummariesByKeys(chunk)
            insertAlbumSummariesForKeys(chunk)
        }
        artistKeys.distinct().chunked(SUMMARY_KEY_BATCH_SIZE).forEach { chunk ->
            deleteArtistSummariesByKeys(chunk)
            insertArtistSummariesForKeys(chunk)
        }
        folderKeys.distinct().chunked(SUMMARY_KEY_BATCH_SIZE).forEach { chunk ->
            deleteFolderSummariesByKeys(chunk)
            insertFolderSummariesForKeys(chunk)
        }
    }

    @Query("SELECT id FROM library_tracks WHERE source = :source AND lastSeenScanRunId != :scanRunId")
    suspend fun getMissingTrackIdsFromSource(source: String, scanRunId: Long): List<String>

    @Query("DELETE FROM library_tracks WHERE source = :source AND lastSeenScanRunId != :scanRunId")
    suspend fun deleteMissingFromSource(source: String, scanRunId: Long): Int

    @Query("DELETE FROM library_tracks WHERE id IN (:trackIds)")
    suspend fun deleteTracksByIds(trackIds: List<String>): Int

    @Query(
        """
        SELECT id FROM library_tracks
        WHERE source = :source
          AND lastSeenScanRunId != :scanRunId
          AND id LIKE 'mediastore:%'
        """,
    )
    suspend fun getMissingNativeMediaStoreTrackIds(source: String, scanRunId: Long): List<String>

    @Query(
        """
        DELETE FROM library_tracks
        WHERE source = :source
          AND lastSeenScanRunId != :scanRunId
          AND id LIKE 'mediastore:%'
        """,
    )
    suspend fun deleteMissingNativeMediaStoreTracks(source: String, scanRunId: Long): Int

    @Query(
        """
        SELECT id FROM library_tracks
        WHERE source = :source
          AND relativePath LIKE :relativePathLike ESCAPE '\'
          AND lastSeenScanRunId != :scanRunId
          AND id LIKE 'saf:%'
        """,
    )
    suspend fun getMissingSafTrackIdsFromRelativePath(
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
          AND id LIKE 'saf:%'
        """,
    )
    suspend fun deleteMissingSafTracksFromRelativePath(
        source: String,
        relativePathLike: String,
        scanRunId: Long,
    ): Int

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

private const val SUMMARY_KEY_BATCH_SIZE = 400
