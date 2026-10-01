package app.echo.android.data

import app.echo.android.model.library.LibraryHealthStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keyset paging keeps inspection memory independent of library size. */
data class LibraryLyricsInspectionTrack(
    val id: String,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val relativePath: String?,
    val fileName: String?,
)

suspend fun EchoLibraryRepository.libraryHealthStats(): LibraryHealthStats = withContext(Dispatchers.IO) {
    database.trackDao().localHealthStats()
}

suspend fun EchoLibraryRepository.lyricsInspectionBatch(afterId: String, lastId: String): List<LibraryLyricsInspectionTrack> =
    withContext(Dispatchers.IO) { database.trackDao().lyricsInspectionBatch(afterId, lastId) }

suspend fun EchoLibraryRepository.lyricsInspectionLastId(): String? =
    withContext(Dispatchers.IO) { database.trackDao().lyricsInspectionLastId() }
