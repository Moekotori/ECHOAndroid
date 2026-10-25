package app.echo.android.data

import androidx.room.withTransaction

internal suspend fun EchoLibraryRepository.applyFolderArtwork(ids: List<String>, uri: String) {
    database.withTransaction {
        val dao = database.trackDao()
        if (dao.applyFolderArtwork(ids, uri) > 0) {
            val keys = LibrarySummaryKeyAccumulator()
            dao.getSummaryKeyRows(ids).forEach { keys.add(it.toSummaryKeySet()) }
            val affected = keys.build()
            dao.rebuildLibrarySummariesForKeys(affected.albumKeys, affected.artistKeys, affected.folderKeys, affected.genreKeys)
        }
    }
}
