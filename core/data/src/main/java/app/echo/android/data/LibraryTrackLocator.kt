package app.echo.android.data

import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Walk only IDs in a bounded cursor window, without materializing the library in memory. */
internal suspend fun findLibraryTrackIndex(
    database: EchoLibraryDatabase,
    orderedIds: SupportSQLiteQuery,
    trackId: String,
): Int? = database.query(orderedIds).use { cursor ->
    while (cursor.moveToNext()) {
        coroutineContext.ensureActive()
        if (cursor.getString(0) == trackId) return@use cursor.position
    }
    null
}
