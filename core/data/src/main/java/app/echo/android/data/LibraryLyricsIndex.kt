package app.echo.android.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LibraryLyricsInspectionResult(
    val track: LibraryLyricsInspectionTrack,
    val hasLyrics: Boolean?,
    val startedAtEpochMs: Long,
)

class LibraryLyricsIndex(private val database: EchoLibraryDatabase) {
    suspend fun summary(): app.echo.android.model.library.LibraryLyricsInspection = withContext(Dispatchers.IO) {
        val row = database.lyricsIndexDao().summary()
        app.echo.android.model.library.LibraryLyricsInspection(row.totalCount, row.foundCount, row.missingCount,
            row.unverifiedCount, row.checkedAtEpochMs)
    }

    suspend fun recordInspection(results: List<LibraryLyricsInspectionResult>) = withContext(Dispatchers.IO) {
        require(results.size <= 64)
        database.withTransaction {
            for (result in results) {
                val track = result.track
                database.lyricsIndexDao().recordInspected(track.id, track.fingerprint, track.contentUri,
                    track.sizeBytes, track.dateModifiedSeconds, result.hasLyrics.toStatus(), result.startedAtEpochMs)
            }
        }
    }

    suspend fun selectionChanged(trackId: String, hasLyrics: Boolean) = withContext(Dispatchers.IO) {
        // Clearing a selected source is unknown: another sidecar/embedded source may still exist.
        database.lyricsIndexDao().recordSelection(trackId, if (hasLyrics) 1 else -1, System.currentTimeMillis())
    }

    suspend fun loaded(track: LibraryTrackEntity, verifiedAt: Long) = withContext(Dispatchers.IO) {
        database.lyricsIndexDao().recordInspected(track.id, track.fingerprint, track.contentUri,
            track.sizeBytes, track.dateModifiedSeconds, 1, verifiedAt)
    }

    private fun Boolean?.toStatus(): Int = when (this) { true -> 1; false -> 0; null -> -1 }
}
