package app.echo.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "library_favorites",
    indices = [
        Index(value = ["favoritedAtEpochMs"]),
    ],
)
data class LibraryFavoriteEntity(
    @PrimaryKey val trackId: String,
    val favoritedAtEpochMs: Long,
)
