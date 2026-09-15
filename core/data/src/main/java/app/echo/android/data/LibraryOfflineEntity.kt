package app.echo.android.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.echo.android.model.library.LibraryOfflineFileStatus
import app.echo.android.model.library.LibraryOfflinePin
import app.echo.android.model.library.LibraryOfflinePinKind
import app.echo.android.model.library.LibraryOfflinePinStatus
import app.echo.android.model.library.LibraryOfflinePolicy

@Entity(
    tableName = "library_offline_pins",
    indices = [Index(value = ["status"]), Index(value = ["createdAtEpochMs"])],
)
data class LibraryOfflinePinEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val source: String,
    val title: String,
    val trackCount: Int,
    val createdAtEpochMs: Long,
    val status: String = LibraryOfflinePinStatus.Queued.id,
    val error: String? = null,
)

@Entity(
    tableName = "library_offline_files",
    indices = [
        Index(value = ["pinId"]),
        Index(value = ["status"]),
    ],
)
data class LibraryOfflineFileEntity(
    @PrimaryKey val trackId: String,
    val pinId: String,
    val remoteUri: String,
    val localPath: String?,
    val bytes: Long = 0L,
    val status: String = LibraryOfflineFileStatus.Queued.id,
    val error: String? = null,
)

data class LibraryOfflineFileReady(
    val trackId: String,
    val localPath: String,
)

fun LibraryOfflinePinEntity.toPin(
    readyCount: Int,
    bytes: Long,
    failedCount: Int,
    downloading: Boolean,
): LibraryOfflinePin =
    LibraryOfflinePin(
        id = id,
        kind = LibraryOfflinePinKind.fromId(kind),
        source = source,
        title = title,
        trackCount = trackCount,
        readyCount = readyCount,
        bytes = bytes,
        status = LibraryOfflinePolicy.status(trackCount, readyCount, failedCount, downloading),
        createdAtEpochMs = createdAtEpochMs,
        error = error,
    )
