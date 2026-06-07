package app.echo.android.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class EchoLibraryRepository(
    private val database: EchoLibraryDatabase,
    private val scanner: MediaStoreTrackScanner,
) {
    fun pagedTracks(query: String? = null): Flow<PagingData<LibraryTrackEntity>> =
        Pager(
            config = PagingConfig(
                pageSize = 60,
                prefetchDistance = 20,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = { database.trackDao().pageTracks(query?.takeIf { it.isNotBlank() }) },
        ).flow

    suspend fun refreshMediaStoreSnapshot(): Int = withContext(Dispatchers.IO) {
        val snapshot = scanner.scanAudio()
        database.trackDao().replaceMediaStoreSnapshot(snapshot)
        snapshot.size
    }

    suspend fun countTracks(): Int = database.trackDao().countTracks()
}
