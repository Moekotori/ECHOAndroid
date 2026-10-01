package app.echo.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import app.echo.android.model.library.EchoSmartPlaylistRule
import app.echo.android.model.library.EchoSmartPlaylistSort
import app.echo.android.model.library.EchoTrackBookmark

@Entity(tableName = "library_smart_rules")
data class LibrarySmartRuleEntity(
    @PrimaryKey val playlistId: String,
    val artist: String,
    val genre: String,
    val favoriteOnly: Boolean,
    val notPlayedDays: Int,
    val minimumYear: Int,
    val maximumYear: Int,
    val sort: String,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "0") val matchAny: Boolean = false,
    @ColumnInfo(defaultValue = "''") val album: String = "",
    @ColumnInfo(defaultValue = "''") val folder: String = "",
    @ColumnInfo(defaultValue = "''") val format: String = "",
    @ColumnInfo(defaultValue = "0") val minimumDurationSeconds: Int = 0,
    @ColumnInfo(defaultValue = "0") val maximumDurationSeconds: Int = 0,
    @ColumnInfo(defaultValue = "''") val excludeText: String = "",
) {
    fun toRule() = EchoSmartPlaylistRule(artist, genre, favoriteOnly, notPlayedDays, minimumYear,
        maximumYear, EchoSmartPlaylistSort.entries.firstOrNull { it.name == sort } ?: EchoSmartPlaylistSort.Title,
        matchAny, album, folder, format, minimumDurationSeconds, maximumDurationSeconds, excludeText)
}

@Entity(tableName = "library_bookmarks", indices = [Index("trackId")])
data class LibraryBookmarkEntity(
    @PrimaryKey val id: String,
    val trackId: String,
    val positionMs: Long,
    val label: String,
    @ColumnInfo(defaultValue = "''") val titleSnapshot: String = "",
    @ColumnInfo(defaultValue = "''") val artistSnapshot: String = "",
    @ColumnInfo(defaultValue = "''") val uriSnapshot: String = "",
    @ColumnInfo(defaultValue = "0") val durationSnapshot: Long = 0,
) {
    fun toBookmark() = EchoTrackBookmark(id, trackId, positionMs, label)
}

@Entity(tableName = "library_repair_archive")
data class LibraryRepairArchiveEntity(@PrimaryKey val id: String, val payload: String, val archivedAt: Long)

data class LibraryMomentRow(val id: String, val trackId: String, val positionMs: Long, val label: String,
    val title: String, val artist: String, val uri: String, val artworkUri: String?, val durationMs: Long, val source: String?) {
    fun toMoment() = app.echo.android.model.library.EchoSavedMoment(
        EchoTrackBookmark(id, trackId, positionMs, label), app.echo.android.model.library.EchoTrack(trackId, uri,
            title.ifBlank { label }, artist, artworkUri = artworkUri, durationMs = durationMs,
            source = app.echo.android.model.library.LibrarySource(source ?: "unknown")))
}
