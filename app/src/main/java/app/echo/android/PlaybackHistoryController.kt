package app.echo.android

import androidx.paging.cachedIn
import app.echo.android.data.EchoLibraryRepository
import app.echo.android.data.ListeningHistoryRepository
import app.echo.android.data.toEchoTrack
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.playback.PlaybackHeatmapDay
import app.echo.android.model.playback.PlaybackHistoryEntry
import app.echo.android.model.playback.PlaybackHistoryFilter
import app.echo.android.model.playback.PlaybackHistorySummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Lifecycle-owned history queries; replay resolves current library data across all sources.
 * [filter] reflects the controls at once, while the list and the summary follow it after the
 * search text settles, so typing never starts one full query per keystroke.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal class PlaybackHistoryController(
    scope: CoroutineScope,
    private val history: ListeningHistoryRepository,
    private val library: EchoLibraryRepository,
    private val play: (EchoTrack) -> Unit,
    private val onPlayNext: (String) -> Unit,
    private val onEnqueue: (String) -> Unit,
) {
    private val filterState = MutableStateFlow(PlaybackHistoryFilter())
    val filter: StateFlow<PlaybackHistoryFilter> = filterState.asStateFlow()

    private var settledQuery = ""
    private val applied = filterState
        .map { it.copy(query = it.query.trim()) }
        .distinctUntilChanged()
        .debounce { if (it.query != settledQuery) SEARCH_DEBOUNCE_MS else 0L }
        .onEach { settledQuery = it.query }
        .stateIn(scope, SharingStarted.Eagerly, PlaybackHistoryFilter())

    val entries = applied.flatMapLatest(history::pagedHistory).cachedIn(scope)

    val summary: StateFlow<PlaybackHistorySummary?> = applied
        .flatMapLatest { history.observeSummary(it) }
        .map<PlaybackHistorySummary, PlaybackHistorySummary?> { it }
        .catch { emit(PlaybackHistorySummary()) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), null)

    /** Plays per day for the trend strip; only the visible window is read. */
    val trend: StateFlow<List<PlaybackHeatmapDay>> = history.observeHeatmap(TREND_DAYS)
        .catch { emit(emptyList()) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    fun update(transform: (PlaybackHistoryFilter) -> PlaybackHistoryFilter) {
        filterState.value = transform(filterState.value)
    }

    fun reset() {
        filterState.value = filterState.value.copy(
            query = "", period = PlaybackHistoryFilter().period, fromEpochDay = null, toEpochDay = null,
        )
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { history.deleteEvent(id) }
    suspend fun clear() = withContext(Dispatchers.IO) { history.clear() }
    suspend fun removeUnavailable(): Int = withContext(Dispatchers.IO) { history.deleteUnavailable() }
    suspend fun exportRows(): List<PlaybackHistoryEntry> = withContext(Dispatchers.IO) { history.exportRows(applied.value) }

    suspend fun replay(trackId: String): Boolean {
        val track = withContext(Dispatchers.IO) { library.trackById(trackId)?.toEchoTrack() }
            ?.takeIf { it.uri.isNotBlank() } ?: return false
        play(track)
        return true
    }

    fun playNext(trackId: String) = onPlayNext(trackId)
    fun enqueue(trackId: String) = onEnqueue(trackId)

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
        const val TREND_DAYS = 14L
    }
}
