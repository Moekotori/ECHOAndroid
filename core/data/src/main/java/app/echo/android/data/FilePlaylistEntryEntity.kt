package app.echo.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Separate from library_tracks so private courses/recordings never enter music queries. */
@Entity(tableName = "file_playlist_entries", indices = [Index(value = ["playlistId", "position"]), Index(value = ["uriKey"])])
data class FilePlaylistEntryEntity(@PrimaryKey val id: String, val playlistId: String, val position: Int,
    val uriKey: String, val durationMs: Long, val document: String)
