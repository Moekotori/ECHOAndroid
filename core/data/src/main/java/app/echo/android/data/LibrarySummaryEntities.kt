package app.echo.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.echo.android.model.library.AlbumSummary

@Entity(
    tableName = "library_album_summaries",
    indices = [
        Index(value = ["isRemote", "title"]),
        Index(value = ["isRemote", "addedAtSeconds"]),
    ],
)
data class LibraryAlbumSummaryEntity(
    @PrimaryKey val albumKey: String,
    val isRemote: Boolean,
    val title: String,
    val albumArtist: String?,
    val artist: String?,
    val artworkUri: String?,
    val trackCount: Int,
    val durationMs: Long,
    val year: Int?,
    val addedAtSeconds: Long,
    val pinyinTitle: String?,
    val pinyinArtist: String?,
)

@Entity(tableName = "library_artist_summaries")
data class LibraryArtistSummaryEntity(
    @PrimaryKey val artistKey: String,
    val name: String,
    val artworkUri: String?,
    val albumCount: Int,
    val trackCount: Int,
    val durationMs: Long,
    val pinyinName: String?,
)

data class LibraryAlbumListenStatsRow(
    val albumKey: String,
    val title: String,
    val albumArtist: String?,
    val artist: String?,
    val artworkUri: String?,
    val trackCount: Int,
    val durationMs: Long,
    val year: Int?,
    val addedAtSeconds: Long,
    val playCount: Int,
    val lastPlayedAtEpochMs: Long,
    val favoritedAtEpochMs: Long,
)

fun LibraryAlbumListenStatsRow.toAlbumSummary(): AlbumSummary =
    AlbumSummary(
        albumKey = albumKey,
        title = title,
        albumArtist = albumArtist,
        artist = artist,
        artworkUri = artworkUri,
        trackCount = trackCount,
        durationMs = durationMs,
        year = year,
        addedAtSeconds = addedAtSeconds,
    )

fun LibraryAlbumListenStatsRow.toListenSeed(): LibraryAlbumListenSeed =
    LibraryAlbumListenSeed(
        albumKey = albumKey,
        playCount = playCount,
        lastPlayedAtEpochMs = lastPlayedAtEpochMs,
        favoritedAtEpochMs = favoritedAtEpochMs,
        addedAtSeconds = addedAtSeconds,
    )

@Entity(tableName = "library_folder_summaries")
data class LibraryFolderSummaryEntity(
    @PrimaryKey val folderKey: String,
    val path: String?,
    val artworkUri: String?,
    val trackCount: Int,
    val albumCount: Int,
    val artistCount: Int,
    val durationMs: Long,
    val totalSizeBytes: Long,
    val latestModifiedSeconds: Long,
)
