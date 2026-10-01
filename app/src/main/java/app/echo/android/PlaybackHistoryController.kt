package app.echo.android

import androidx.paging.cachedIn
import app.echo.android.data.EchoLibraryRepository
import app.echo.android.data.ListeningHistoryRepository
import app.echo.android.data.toEchoTrack
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.playback.ListeningStatsRange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext

/** Lifecycle-owned history queries; replay resolves current library data across all sources. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal class PlaybackHistoryController(
    scope: CoroutineScope,
    private val history: ListeningHistoryRepository,
    private val library: EchoLibraryRepository,
    private val play: (EchoTrack) -> Unit,
) {
    private val queryState = MutableStateFlow("")
    private val rangeState = MutableStateFlow(ListeningStatsRange.All)
    val query = queryState.asStateFlow()
    val range = rangeState.asStateFlow()
    val entries = combine(queryState.debounce(250L), rangeState) { query, range -> query.trim() to range }
        .distinctUntilChanged()
        .flatMapLatest { (query, range) -> history.pagedHistory(query, range) }
        .cachedIn(scope)

    fun setQuery(query: String) { queryState.value = query }
    fun setRange(range: ListeningStatsRange) { rangeState.value = range }
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { history.deleteEvent(id) }
    suspend fun clear() = withContext(Dispatchers.IO) { history.clear() }

    suspend fun replay(trackId: String): Boolean {
        val track = withContext(Dispatchers.IO) { library.trackById(trackId)?.toEchoTrack() }
            ?.takeIf { it.uri.isNotBlank() } ?: return false
        play(track)
        return true
    }
}
