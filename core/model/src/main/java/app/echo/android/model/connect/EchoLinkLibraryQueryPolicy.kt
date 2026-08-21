package app.echo.android.model.connect

object EchoLinkLibraryQueryPolicy {
    fun remoteSearchQuery(localQuery: String): String {
        val trimmed = localQuery.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.any(::isCjk)) return trimmed
        return ""
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

    private fun isCjk(ch: Char): Boolean =
        ch in '\u3400'..'\u4DBF' ||
            ch in '\u4E00'..'\u9FFF' ||
            ch in '\uF900'..'\uFAFF'
}
