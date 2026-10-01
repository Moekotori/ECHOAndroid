package app.echo.android.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "library_track_artists",
    primaryKeys = ["trackId", "artistKey"],
    indices = [Index(value = ["artistKey"])],
    foreignKeys = [ForeignKey(
        entity = LibraryTrackEntity::class,
        parentColumns = ["id"], childColumns = ["trackId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class LibraryTrackArtistEntity(
    val trackId: String,
    val artistKey: String,
    val name: String,
    val pinyinName: String?,
)
