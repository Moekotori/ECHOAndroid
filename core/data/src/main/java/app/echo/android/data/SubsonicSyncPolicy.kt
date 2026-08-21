package app.echo.android.data

object SubsonicSyncPolicy {
    const val AlbumFetchConcurrency = 8
    const val PlaylistFetchConcurrency = 4

    val Search3QueryAttempts = listOf("", "*")

    fun shouldPreferSearch3Bulk(expectedSongCount: Int, bulkSongCount: Int): Boolean {
        if (expectedSongCount <= 0) return false
        if (bulkSongCount <= 0) return false
        return bulkSongCount >= expectedSongCount
    }

    fun shouldAuthorizeMissingRowDeletion(
        usedSearch3: Boolean,
        expectedSongCount: Int,
        bulkSongCount: Int,
        existingRemoteCount: Int,
        hitVisitCap: Boolean = false,
    ): Boolean {
        if (hitVisitCap) return false
        if (!usedSearch3) return true
        if (expectedSongCount <= 0) return false
        if (bulkSongCount < expectedSongCount) return false
        if (existingRemoteCount > 0 && bulkSongCount < existingRemoteCount) return false
        return true
    }

    fun shouldReplaceSyncedPlaylist(
        fetchSucceeded: Boolean,
        remoteSongCount: Int,
        matchedTrackCount: Int,
    ): Boolean {
        if (!fetchSucceeded) return false
        if (remoteSongCount > 0 && matchedTrackCount <= 0) return false
        return true
    }

    fun shouldRewriteSyncedPlaylist(
        existingName: String?,
        existingTrackIds: List<String>?,
        incomingName: String,
        incomingTrackIds: List<String>,
    ): Boolean {
        if (existingTrackIds == null) return true
        if (existingName != incomingName) return true
        return existingTrackIds != incomingTrackIds
    }
}
