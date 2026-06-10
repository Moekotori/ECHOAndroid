package app.echo.android.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.echo.android.model.library.EchoPlaylist
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryPlaylistDao {
    @Query(
        """
        SELECT id, name, trackCount, artworkUri, updatedAtEpochMs
        FROM library_playlists
        WHERE source = :source
        ORDER BY updatedAtEpochMs DESC, name COLLATE NOCASE ASC
        """,
    )
    fun observePlaylists(source: String): Flow<List<PlaylistSummaryRow>>

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: LibraryPlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(tracks: List<LibraryPlaylistTrackEntity>)

    @Query("DELETE FROM library_playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deletePlaylistTracks(playlistId: String)

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
}

data class PlaylistSummaryRow(
    val id: String,
    val name: String,
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
    )
