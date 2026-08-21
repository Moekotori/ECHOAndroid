package app.echo.android.playback

object PlaybackCatalogPolicy {
    fun hasPlayableUri(uri: String?): Boolean = !uri.isNullOrBlank()

    fun shouldExpandCatalogQueue(mediaId: String, hasPlayUri: Boolean): Boolean {
        if (mediaId.isBlank()) return false
        if (EchoPlaybackLibraryIds.isBrowsableCollection(mediaId)) return true
        if (hasPlayUri) return false
        return EchoPlaybackLibraryIds.isTrackMediaId(mediaId)
    }

    fun resolvedStartIndex(
        requestedIds: List<String>,
        resolvedIds: List<String>,
        startIndex: Int,
    ): Int {
        if (resolvedIds.isEmpty()) return 0
        if (requestedIds.size == 1) {
            return PlaybackSessionPolicy.queueStartIndex(resolvedIds, requestedIds.first())
        }
        val requestedId = requestedIds.getOrNull(startIndex)
        if (!requestedId.isNullOrBlank()) {
            val mapped = PlaybackSessionPolicy.queueStartIndex(resolvedIds, requestedId)
            if (resolvedIds.getOrNull(mapped) == requestedId) return mapped
        }
        return startIndex.coerceIn(0, resolvedIds.lastIndex)
    }
}
