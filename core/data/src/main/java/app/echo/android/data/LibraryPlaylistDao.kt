package app.echo.android.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.LibrarySource
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryPlaylistDao {
    @RawQuery(observedEntities = [LibraryTrackEntity::class, LibraryPlaylistTrackEntity::class,
        LibraryFavoriteEntity::class, LibraryPlaybackStatsEntity::class, LibrarySmartRuleEntity::class, LibraryHiddenTrackEntity::class])
    fun observeStats(query: SupportSQLiteQuery): Flow<app.echo.android.model.library.PlaylistStats>

    @RawQuery(observedEntities = [LibraryTrackEntity::class, LibraryPlaylistTrackEntity::class,
        LibraryFavoriteEntity::class, LibraryPlaybackStatsEntity::class, LibraryHiddenTrackEntity::class])
    fun pageOrderedTracks(query: SupportSQLiteQuery): PagingSource<Int, LibraryTrackEntity>

    @Query("SELECT * FROM library_playlist_tracks WHERE playlistId = :id AND trackId = :trackId LIMIT 1")
    suspend fun membership(id: String, trackId: String): LibraryPlaylistTrackEntity?

    @Query("SELECT * FROM library_playlist_tracks WHERE playlistId = :id AND position < :position ORDER BY position DESC LIMIT 1")
    suspend fun previousMembership(id: String, position: Int): LibraryPlaylistTrackEntity?

    @Query("SELECT * FROM library_playlist_tracks WHERE playlistId = :id AND position > :position ORDER BY position ASC LIMIT 1")
    suspend fun nextMembership(id: String, position: Int): LibraryPlaylistTrackEntity?

    @Query("UPDATE library_playlist_tracks SET position = :position WHERE playlistId = :id AND trackId = :trackId")
    suspend fun setPosition(id: String, trackId: String, position: Int)

    @Query("UPDATE library_playlist_tracks SET position = position + :delta WHERE playlistId = :id AND position >= :from AND position <= :to")
    suspend fun shiftPositions(id: String, from: Int, to: Int, delta: Int)

    @Query("SELECT COALESCE(MAX(position), -1) FROM library_playlist_tracks WHERE playlistId = :id")
    suspend fun lastPosition(id: String): Int

    @Query("SELECT trackId FROM library_playlist_tracks WHERE playlistId = :id AND trackId IN (:ids)")
    suspend fun existingMembers(id: String, ids: List<String>): List<String>

    @Query("UPDATE library_playlists SET trackCount = (SELECT COUNT(*) FROM library_playlist_tracks WHERE playlistId = :id), artworkUri = COALESCE(artworkUri, (SELECT t.artworkUri FROM library_tracks t JOIN library_playlist_tracks p ON p.trackId = t.id WHERE p.playlistId = :id AND t.artworkUri IS NOT NULL ORDER BY p.position LIMIT 1)), updatedAtEpochMs = :now WHERE id = :id")
    suspend fun refreshMembershipSummary(id: String, now: Long)

    @Query("DELETE FROM library_playlist_tracks WHERE playlistId = :id AND trackId = :trackId")
    suspend fun removeMember(id: String, trackId: String): Int

    @Query("UPDATE library_playlists SET artworkUri = :uri, updatedAtEpochMs = :now WHERE id = :id")
    suspend fun setArtwork(id: String, uri: String, now: Long)

    @Query("UPDATE library_playlists SET name = :name, updatedAtEpochMs = :now WHERE id = :id")
    suspend fun setName(id: String, name: String, now: Long)

    @Query("SELECT id, name, source, trackCount, artworkUri, updatedAtEpochMs FROM library_playlists WHERE name LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE LIMIT 30")
    suspend fun searchEveryPlaylist(query: String): List<PlaylistSummaryRow>

    @Query("SELECT id, name, source, trackCount, artworkUri, updatedAtEpochMs FROM library_playlists WHERE name LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE, id")
    fun pageSearchPlaylists(query: String): PagingSource<Int, PlaylistSummaryRow>
    @Query(
        """
        SELECT id, name, source, trackCount, artworkUri, updatedAtEpochMs
        FROM library_playlists
        WHERE source = :source
        ORDER BY updatedAtEpochMs DESC, name COLLATE NOCASE ASC
        """,
    )
    fun observePlaylists(source: String): Flow<List<PlaylistSummaryRow>>

    @Query(
        """
        SELECT id, name, source, trackCount, artworkUri, updatedAtEpochMs
        FROM library_playlists
        ORDER BY CASE WHEN source = 'mediastore' THEN 0 ELSE 1 END,
                 updatedAtEpochMs DESC,
                 name COLLATE NOCASE ASC
        """,
    )
    fun observeAllPlaylists(): Flow<List<PlaylistSummaryRow>>

    @Query("SELECT id FROM library_playlists WHERE source = :source")
    suspend fun getPlaylistIdsFromSource(source: String): List<String>

    @Query("SELECT * FROM library_playlists WHERE source = :source ORDER BY name COLLATE NOCASE ASC")
    suspend fun getPlaylistsBySource(source: String): List<LibraryPlaylistEntity>

    @Query(
        """
        SELECT library_tracks.* FROM visible_library_tracks AS library_tracks
        JOIN library_playlist_tracks ON library_tracks.id = library_playlist_tracks.trackId
        WHERE library_playlist_tracks.playlistId = :playlistId
        ORDER BY library_playlist_tracks.position ASC
        """,
    )
    fun pagePlaylistTracks(playlistId: String): PagingSource<Int, LibraryTrackEntity>

    @Query(
        """
        SELECT library_tracks.* FROM library_tracks
        JOIN library_playlist_tracks ON library_tracks.id = library_playlist_tracks.trackId
        WHERE library_playlist_tracks.playlistId = :playlistId
        ORDER BY library_playlist_tracks.position ASC
        LIMIT :limit
        """,
    )
    suspend fun getPlaylistTracksForPlayback(playlistId: String, limit: Int): List<LibraryTrackEntity>

    @Query("SELECT * FROM library_playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylist(playlistId: String): LibraryPlaylistEntity?

    @Query(
        """
        SELECT trackId FROM library_playlist_tracks
        WHERE playlistId = :playlistId
        ORDER BY position ASC
        """,
    )
    suspend fun getPlaylistTrackIds(playlistId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: LibraryPlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(tracks: List<LibraryPlaylistTrackEntity>)

    @Query("DELETE FROM library_playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deletePlaylistTracks(playlistId: String)

    @Query("DELETE FROM library_playlists WHERE id = :playlistId")
    suspend fun deletePlaylistRow(playlistId: String)

    @Transaction
    suspend fun replacePlaylist(
        playlist: LibraryPlaylistEntity,
        tracks: List<LibraryPlaylistTrackEntity>,
    ) {
        upsertPlaylist(playlist)
        deletePlaylistTracks(playlist.id)
        if (tracks.isNotEmpty()) {
            tracks.chunked(500).forEach { insertPlaylistTracks(it) }
        }
    }

    @Transaction
    suspend fun deletePlaylist(playlistId: String) {
        deletePlaylistTracks(playlistId)
        deletePlaylistRow(playlistId)
    }

    @Query("SELECT trackId FROM library_favorites ORDER BY favoritedAtEpochMs DESC")
    fun observeFavoriteTrackIds(): Flow<List<String>>

    @Query("SELECT trackId FROM library_favorites")
    suspend fun getFavoriteTrackIds(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM library_favorites WHERE trackId = :trackId)")
    suspend fun isFavorite(trackId: String): Boolean

    @Query(
        """
        SELECT id, name, source, trackCount, artworkUri, updatedAtEpochMs
        FROM library_playlists
        WHERE source = :source
        ORDER BY updatedAtEpochMs DESC, name COLLATE NOCASE ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listPlaylistsForBrowse(
        source: String,
        limit: Int,
        offset: Int,
    ): List<PlaylistSummaryRow>

    @Query(
        """
        SELECT id, name, source, trackCount, artworkUri, updatedAtEpochMs
        FROM library_playlists
        WHERE source = :source
          AND name LIKE '%' || :query || '%'
        ORDER BY
            CASE WHEN name LIKE :query || '%' THEN 0 ELSE 1 END,
            name COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    suspend fun searchPlaylistsForBrowse(
        source: String,
        query: String,
        limit: Int,
    ): List<PlaylistSummaryRow>

    @Query(
        """
        SELECT library_tracks.* FROM visible_library_tracks AS library_tracks
        JOIN library_playlist_tracks ON library_tracks.id = library_playlist_tracks.trackId
        WHERE library_playlist_tracks.playlistId = :playlistId
        ORDER BY library_playlist_tracks.position ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listPlaylistTracksForBrowse(
        playlistId: String,
        limit: Int,
        offset: Int,
    ): List<LibraryTrackEntity>

    @Query(
        """
        SELECT library_tracks.* FROM visible_library_tracks AS library_tracks
        INNER JOIN library_favorites f ON f.trackId = library_tracks.id
        ORDER BY f.favoritedAtEpochMs DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listFavoriteTracksForBrowse(limit: Int, offset: Int): List<LibraryTrackEntity>

    @Query(
        """
        SELECT library_tracks.* FROM visible_library_tracks AS library_tracks
        INNER JOIN library_favorites f ON f.trackId = library_tracks.id
        ORDER BY f.favoritedAtEpochMs DESC
        """,
    )
    fun pageFavoriteTracks(): PagingSource<Int, LibraryTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(favorite: LibraryFavoriteEntity)

    @Query("DELETE FROM library_favorites WHERE trackId = :trackId")
    suspend fun deleteFavorite(trackId: String)

    @Query(
        """
        SELECT s.albumKey, s.title, s.albumArtist, s.artist, s.artworkUri,
               s.trackCount, s.durationMs, s.year, s.addedAtSeconds
        FROM library_album_summaries s
        INNER JOIN visible_library_tracks t ON t.albumKey = s.albumKey
        INNER JOIN library_favorites f ON f.trackId = t.id
        WHERE s.isRemote = 0
        GROUP BY s.albumKey
        ORDER BY MAX(f.favoritedAtEpochMs) DESC
        LIMIT :limit
        """,
    )
    fun observeFavoriteAlbums(limit: Int): Flow<List<AlbumSummary>>
}

data class PlaylistSummaryRow(
    val id: String,
    val name: String,
    val source: String = LibrarySource.MediaStore.id,
    val trackCount: Int,
    val artworkUri: String?,
    val updatedAtEpochMs: Long,
)

fun PlaylistSummaryRow.toEchoPlaylist(trackIds: List<String> = emptyList()): EchoPlaylist =
    EchoPlaylist(
        id = id,
        name = name,
        trackIds = trackIds,
        trackCount = trackCount,
        artworkUri = artworkUri,
        updatedAtEpochMs = updatedAtEpochMs,
        source = source,
    )
