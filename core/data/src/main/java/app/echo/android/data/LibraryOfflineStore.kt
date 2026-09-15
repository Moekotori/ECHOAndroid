package app.echo.android.data

import app.echo.android.model.library.LibraryOfflineFileStatus
import app.echo.android.model.library.LibraryOfflinePin
import app.echo.android.model.library.LibraryOfflinePinKind
import app.echo.android.model.library.LibraryOfflinePinStatus
import app.echo.android.model.library.LibraryOfflinePolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.security.MessageDigest

class LibraryOfflineStore(
    private val database: EchoLibraryDatabase,
    private val directory: File,
) {
    private val dao get() = database.offlineDao()

    fun observePin(pinId: String): Flow<LibraryOfflinePin?> = flow {
        dao.observePin(pinId).collect { pin -> emit(pin?.let { loadPin(it) }) }
    }

    fun observePins(): Flow<List<LibraryOfflinePin>> = flow {
        dao.observePins().collect { rows -> emit(rows.map { loadPin(it) }) }
    }

    suspend fun usedBytes(): Long = dao.readyBytes()

    suspend fun readyPathMap(): Map<String, String> =
        dao.readyFiles().mapNotNull { row ->
            val path = row.localPath.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (!File(path).isFile) return@mapNotNull null
            row.trackId to path
        }.toMap()

    suspend fun queuedFiles(limit: Int = 16): List<LibraryOfflineFileEntity> =
        dao.filesWithStatus(LibraryOfflineFileStatus.Queued.id, limit)

    suspend fun pinAlbum(albumKey: String, title: String): LibraryOfflinePin? {
        val tracks = albumTracks(albumKey)
        return pinTracks(
            pinId = LibraryOfflinePolicy.albumPinId(albumKey),
            kind = LibraryOfflinePinKind.Album,
            title = title,
            tracks = tracks,
        )
    }

    suspend fun pinPlaylist(playlistId: String, title: String): LibraryOfflinePin? {
        val tracks = database.playlistDao()
            .getPlaylistTracksForPlayback(playlistId, LibraryOfflinePolicy.MaxTracksPerPin)
        return pinTracks(
            pinId = LibraryOfflinePolicy.playlistPinId(playlistId),
            kind = LibraryOfflinePinKind.Playlist,
            title = title,
            tracks = tracks,
        )
    }

    suspend fun pinTrack(track: LibraryTrackEntity): LibraryOfflinePin? =
        pinTracks(
            pinId = LibraryOfflinePolicy.trackPinId(track.id),
            kind = LibraryOfflinePinKind.Track,
            title = track.title,
            tracks = listOf(track),
        )

    suspend fun unpin(pinId: String) {
        val paths = dao.localPathsForPin(pinId)
        dao.deleteFilesForPin(pinId)
        dao.deletePin(pinId)
        paths.forEach { path -> runCatching { File(path).delete() } }
    }

    suspend fun markDownloading(trackId: String) {
        dao.setFileStatus(trackId, LibraryOfflineFileStatus.Downloading.id, null)
        refreshPinForTrack(trackId)
    }

    suspend fun markReady(trackId: String, file: File) {
        dao.setFileReady(
            trackId = trackId,
            localPath = file.absolutePath,
            bytes = file.length(),
            status = LibraryOfflineFileStatus.Ready.id,
        )
        refreshPinForTrack(trackId)
    }

    suspend fun markFailed(trackId: String, error: String) {
        dao.setFileStatus(trackId, LibraryOfflineFileStatus.Failed.id, error.take(160))
        refreshPinForTrack(trackId)
    }

    suspend fun destinationFile(trackId: String): File {
        directory.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256").digest(trackId.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(directory, digest)
    }

    private suspend fun albumTracks(albumKey: String): List<LibraryTrackEntity> {
        val remote = parseRemoteAlbumKey(albumKey)
        return if (remote != null) {
            database.trackDao().getTracksByRemoteAlbum(remote.first, remote.second)
        } else {
            database.trackDao().getTracksByAlbum(albumKey)
        }
    }

    private suspend fun pinTracks(
        pinId: String,
        kind: LibraryOfflinePinKind,
        title: String,
        tracks: List<LibraryTrackEntity>,
    ): LibraryOfflinePin? {
        val pinnable = tracks
            .filter { LibraryOfflinePolicy.canPinSource(it.source) }
            .take(LibraryOfflinePolicy.MaxTracksPerPin)
        if (pinnable.isEmpty()) return null
        val source = pinnable.groupingBy { it.source }.eachCount().maxBy { it.value }.key
        val pin = LibraryOfflinePinEntity(
            id = pinId,
            kind = kind.id,
            source = source,
            title = title.trim().ifBlank { pinId },
            trackCount = pinnable.size,
            createdAtEpochMs = System.currentTimeMillis(),
            status = LibraryOfflinePinStatus.Queued.id,
        )
        val files = pinnable.map { track ->
            val existing = dao.readyPath(track.id)?.let(::File)?.takeIf { it.isFile }
            LibraryOfflineFileEntity(
                trackId = track.id,
                pinId = pinId,
                remoteUri = track.contentUri,
                localPath = existing?.absolutePath,
                bytes = existing?.length() ?: 0L,
                status = if (existing != null) {
                    LibraryOfflineFileStatus.Ready.id
                } else {
                    LibraryOfflineFileStatus.Queued.id
                },
            )
        }
        dao.replacePin(pin, files)
        val created = loadPin(pin)
        dao.setPinStatus(pinId, created.status.id, created.error)
        return created
    }

    private suspend fun refreshPinForTrack(trackId: String) {
        val pinId = dao.pinIdForTrack(trackId) ?: return
        refreshPin(pinId)
    }

    private suspend fun loadPin(pin: LibraryOfflinePinEntity): LibraryOfflinePin =
        pin.toPin(
            readyCount = dao.readyCount(pin.id),
            bytes = dao.readyBytesForPin(pin.id),
            failedCount = dao.failedCount(pin.id),
            downloading = dao.downloadingCount(pin.id) > 0,
        )

    private suspend fun refreshPin(pinId: String) {
        val pin = dao.getPin(pinId) ?: return
        val next = loadPin(pin)
        dao.setPinStatus(pinId, next.status.id, next.error)
    }
}

internal fun parseRemoteAlbumKey(value: String): Pair<String, String>? {
    if (!value.startsWith("remote||")) return null
    val parts = value.split("||", limit = 3)
    if (parts.size != 3 || parts[1].isBlank() || parts[2].isBlank()) return null
    return parts[1] to parts[2]
}
