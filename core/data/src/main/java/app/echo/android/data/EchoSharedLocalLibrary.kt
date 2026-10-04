package app.echo.android.data

import androidx.room.withTransaction

/** Read-only view of complete local files; remote sources and CUE clips cannot be re-exported. */
class EchoSharedLocalLibrary(private val database: EchoLibraryDatabase) {
    suspend fun page(query: String, offset: Int, limit: Int): Pair<List<LibraryTrackEntity>, Int> {
        val needle = query.trim().take(256)
        val pattern = "%" + needle.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return database.withTransaction {
            val dao = database.trackDao()
            dao.sharedLocalTracks(needle, pattern, limit.coerceIn(1, 100), offset.coerceAtLeast(0)) to
                dao.sharedLocalTrackCount(needle, pattern)
        }
    }

    suspend fun track(id: String): LibraryTrackEntity? = database.trackDao().getTrackById(id)?.takeIf {
        it.source in setOf("mediastore", "saf") && !app.echo.android.model.library.CueSheetPolicy.isCueTrackId(it.id) && it.clipStartMs == 0L && it.clipEndMs == 0L &&
            (it.contentUri.startsWith("content:") || it.contentUri.startsWith("file:"))
    }
}
