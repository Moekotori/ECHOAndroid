package app.echo.android.model.library

import java.security.MessageDigest

enum class LibraryOfflinePinKind(val id: String) {
    Album("album"),
    Playlist("playlist"),
    Track("track"),
    ;

    companion object {
        fun fromId(value: String?): LibraryOfflinePinKind =
            entries.firstOrNull { it.id == value } ?: Track
    }
}

enum class LibraryOfflineFileStatus(val id: String) {
    Queued("queued"),
    Downloading("downloading"),
    Ready("ready"),
    Failed("failed"),
    ;

    companion object {
        fun fromId(value: String?): LibraryOfflineFileStatus =
            entries.firstOrNull { it.id == value } ?: Queued
    }
}

enum class LibraryOfflinePinStatus(val id: String) {
    Queued("queued"),
    Downloading("downloading"),
    Ready("ready"),
    Partial("partial"),
    Failed("failed"),
    ;

    companion object {
        fun fromId(value: String?): LibraryOfflinePinStatus =
            entries.firstOrNull { it.id == value } ?: Queued
    }
}

data class LibraryOfflinePin(
    val id: String,
    val kind: LibraryOfflinePinKind,
    val source: String,
    val title: String,
    val trackCount: Int,
    val readyCount: Int = 0,
    val bytes: Long = 0L,
    val status: LibraryOfflinePinStatus = LibraryOfflinePinStatus.Queued,
    val createdAtEpochMs: Long = 0L,
    val error: String? = null,
)

object LibraryOfflinePolicy {
    const val DefaultQuotaBytes = 8L * 1024L * 1024L * 1024L
    const val MinFreeSpaceBytes = 256L * 1024L * 1024L
    const val MaxFileBytes = 512L * 1024L * 1024L
    const val MaxTracksPerPin = 200
    const val MaxConcurrentDownloads = 2

    fun canPinSource(source: String?): Boolean {
        val id = source?.trim().orEmpty()
        return id == LibrarySource.Subsonic.id ||
            id == LibrarySource.Jellyfin.id ||
            id == LibrarySource.WebDav.id ||
            id == LibrarySource.EchoLink.id
    }

    fun remoteAlbumParts(albumKey: String): Pair<String, String>? {
        if (!albumKey.startsWith("remote||")) return null
        val parts = albumKey.split("||", limit = 3)
        if (parts.size != 3 || parts[1].isBlank() || parts[2].isBlank()) return null
        return parts[1] to parts[2]
    }

    fun canPinAlbumKey(albumKey: String): Boolean = canPinSource(remoteAlbumParts(albumKey)?.first)

    fun fileNameForTrack(trackId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(trackId.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun pinId(kind: LibraryOfflinePinKind, targetKey: String): String =
        "${kind.id}:${targetKey.trim()}"

    fun albumPinId(albumKey: String): String = pinId(LibraryOfflinePinKind.Album, albumKey)

    fun playlistPinId(playlistId: String): String = pinId(LibraryOfflinePinKind.Playlist, playlistId)

    fun trackPinId(trackId: String): String = pinId(LibraryOfflinePinKind.Track, trackId)

    fun status(
        trackCount: Int,
        readyCount: Int,
        failedCount: Int,
        downloading: Boolean,
    ): LibraryOfflinePinStatus {
        val tracks = trackCount.coerceAtLeast(0)
        val ready = readyCount.coerceIn(0, tracks)
        if (tracks <= 0) return LibraryOfflinePinStatus.Failed
        if (ready >= tracks) return LibraryOfflinePinStatus.Ready
        if (downloading) return LibraryOfflinePinStatus.Downloading
        if (ready > 0) return LibraryOfflinePinStatus.Partial
        if (failedCount > 0) return LibraryOfflinePinStatus.Failed
        return LibraryOfflinePinStatus.Queued
    }

    fun quotaAllows(usedBytes: Long, incomingBytes: Long, quotaBytes: Long = DefaultQuotaBytes): Boolean {
        if (incomingBytes <= 0L) return true
        if (incomingBytes > MaxFileBytes) return false
        val used = usedBytes.coerceAtLeast(0L)
        val quota = quotaBytes.coerceAtLeast(0L)
        return used + incomingBytes <= quota
    }

    fun remainingQuota(usedBytes: Long, quotaBytes: Long = DefaultQuotaBytes): Long =
        (quotaBytes - usedBytes.coerceAtLeast(0L)).coerceAtLeast(0L)
}
