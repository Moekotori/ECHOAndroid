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
        resolvedCounts: IntArray,
    ): Int {
        if (resolvedIds.isEmpty() || requestedIds.isEmpty()) return 0
        val selected = startIndex.coerceIn(requestedIds.indices)
        var offset = 0
        for (index in 0 until selected) offset += resolvedCounts[index]
        // Search only the items expanded from the selected entry. IDs can repeat across entries.
        for (index in offset until offset + resolvedCounts[selected]) {
            if (resolvedIds[index] == requestedIds[selected]) return index
        }
        return offset.coerceAtMost(resolvedIds.lastIndex)
    }
}
