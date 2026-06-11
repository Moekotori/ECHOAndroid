package app.echo.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "library_playback_stats",
    indices = [
        Index(value = ["playCount"]),
        Index(value = ["lastPlayedAtEpochMs"]),
    ],
)
data class LibraryPlaybackStatsEntity(
    @PrimaryKey val trackId: String,
    val playCount: Int,
    val lastPlayedAtEpochMs: Long,
)
