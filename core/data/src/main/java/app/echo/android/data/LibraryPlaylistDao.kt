package app.echo.android.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.echo.android.model.library.AlbumSummary
import app.echo.android.model.library.EchoPlaylist
import app.echo.android.model.library.LibrarySource
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryPlaylistDao {
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

    @Query(
        """
        SELECT library_tracks.* FROM library_tracks
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
        SELECT library_tracks.* FROM library_tracks
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
        SELECT library_tracks.* FROM library_tracks
        INNER JOIN library_favorites f ON f.trackId = library_tracks.id
        ORDER BY f.favoritedAtEpochMs DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listFavoriteTracksForBrowse(limit: Int, offset: Int): List<LibraryTrackEntity>

    @Query(
        """
        SELECT library_tracks.* FROM library_tracks
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
        INNER JOIN library_tracks t ON t.albumKey = s.albumKey
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
