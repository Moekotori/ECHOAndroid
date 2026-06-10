package app.echo.android.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "library_playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    indices = [
        Index(value = ["trackId"]),
        Index(value = ["playlistId", "position"]),
    ],
)
data class LibraryPlaylistTrackEntity(
    val playlistId: String,
    val trackId: String,
    val position: Int,
)
