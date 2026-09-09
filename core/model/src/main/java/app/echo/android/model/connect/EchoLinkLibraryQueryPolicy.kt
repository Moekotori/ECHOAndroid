package app.echo.android.model.connect

object EchoLinkLibraryQueryPolicy {
    const val RemoteAlbumKeyPrefix = "echo-link-album:"

    fun remoteSearchQuery(localQuery: String): String = localQuery.trim()

    fun remoteAlbumKey(albumId: String): String = RemoteAlbumKeyPrefix + albumId.trim()

    fun remoteAlbumId(albumKey: String): String? {
        if (!albumKey.startsWith(RemoteAlbumKeyPrefix)) return null
        return albumKey.removePrefix(RemoteAlbumKeyPrefix).takeIf { it.isNotBlank() }
    }

    fun shouldFetchPlaylistTracks(
        knownTrackCount: Int,
        declaredTrackCount: Int,
    ): Boolean {
        if (declaredTrackCount > 0) return knownTrackCount < declaredTrackCount
        return knownTrackCount <= 0
    }

    fun playableLinkedPhoneTracks(tracks: List<EchoRemoteTrack>): List<EchoRemoteTrack> =
        tracks.filter { !it.id.isNullOrBlank() && it.canPlayOnPhone }

    fun playableLinkedPcTrackIds(tracks: List<EchoRemoteTrack>): List<String> =
        tracks.mapNotNull { track -> track.id?.takeIf { it.isNotBlank() } }

    fun queueReplaceStartId(trackIds: List<String>, requestedId: String?): String? {
        val requested = requestedId?.takeIf { it.isNotBlank() }
        if (requested != null && requested in trackIds) return requested
        return trackIds.firstOrNull()
    }

    fun parentFolderPath(path: String): String {
        val trimmed = path.trim().trim('/')
        if (trimmed.isEmpty()) return ""
        val separator = trimmed.lastIndexOf('/')
        return if (separator <= 0) "" else trimmed.substring(0, separator)
    }

    fun shouldKeepLoadedTracksForQuery(query: String, previousTrackCount: Int): Boolean {
        if (previousTrackCount <= 0) return false
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false
        return !trimmed.any(::isCjk)
    }

    fun shouldKeepPreviousTracksOnEmptyRemotePage(
        query: String,
        remoteTrackCount: Int,
        previousTrackCount: Int,
    ): Boolean {
        if (remoteTrackCount > 0) return false
        return shouldKeepLoadedTracksForQuery(query, previousTrackCount)
    }

    private fun isCjk(ch: Char): Boolean =
        ch in '\u3400'..'\u4DBF' ||
            ch in '\u4E00'..'\u9FFF' ||
            ch in '\uF900'..'\uFAFF'
}
