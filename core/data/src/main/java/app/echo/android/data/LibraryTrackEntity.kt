package app.echo.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "library_tracks",
    indices = [
        Index(value = ["title"]),
        Index(value = ["album"]),
        Index(value = ["artist"]),
        Index(value = ["contentUri"], unique = true),
    ],
)
data class LibraryTrackEntity(
    @PrimaryKey val id: String,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String?,
    val albumArtist: String?,
    val artworkUri: String?,
    val durationMs: Long,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val mimeType: String?,
    val sizeBytes: Long,
    val dateModifiedSeconds: Long,
    val source: String = "mediastore",
)
