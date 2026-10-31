package app.echo.android.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Availability evidence only; lyric text remains in its existing owner/store. */
@Entity(
    tableName = "library_lyrics_index",
    foreignKeys = [ForeignKey(entity = LibraryTrackEntity::class, parentColumns = ["id"],
        childColumns = ["trackId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["status", "trackId"])],
)
data class LibraryLyricsIndexEntity(
    @PrimaryKey val trackId: String,
    val trackFingerprint: String?,
    val contentUri: String,
    val sizeBytes: Long,
    val dateModifiedSeconds: Long,
    /** 1 = verified present, 0 = verified absent, -1 = unknown/unreadable. */
    val status: Int,
    val checkedAtEpochMs: Long,
)
