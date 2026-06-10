package app.echo.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "library_playlists",
    indices = [
        Index(value = ["source"]),
        Index(value = ["updatedAtEpochMs"]),
    ],
)
data class LibraryPlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val source: String,
    val artworkUri: String?,
    val trackCount: Int,
    val updatedAtEpochMs: Long,
)
