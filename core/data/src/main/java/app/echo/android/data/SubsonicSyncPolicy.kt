package app.echo.android.data

object SubsonicSyncPolicy {
    const val AlbumFetchConcurrency = 4

    fun shouldPreferSearch3Bulk(expectedSongCount: Int, bulkSongCount: Int): Boolean {
        if (bulkSongCount <= 0) return false
        if (expectedSongCount <= 0) return true
        return bulkSongCount >= expectedSongCount
    }
}
