package app.echo.android.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.InvalidationTracker
import androidx.room.withTransaction
import app.echo.android.model.library.LibraryTrackFilter
import app.echo.android.model.library.LibraryTrackSortMode
import app.echo.android.model.library.LibraryPlayedFilter
import app.echo.android.model.library.LibraryPresenceFilter
import java.lang.ref.WeakReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class LibraryTrackWindow<T : Any>(val data: List<T>, val count: Int, val offset: Int)

/** Count once per database snapshot; fetch bounded windows without Room's sorted COUNT subquery. */
internal class LibraryTrackPagingSource<T : Any>(
    private val readWindow: suspend (offset: Int, limit: Int, knownCount: Int?) -> LibraryTrackWindow<T>,
    private val observe: (LibraryTrackPagingSource<T>) -> Unit = {},
) : PagingSource<Int, T>() {
    private val mutex = Mutex()
    private var count: Int? = null
    private var observing = false

    override val jumpingSupported: Boolean = true

    override fun getRefreshKey(state: PagingState<Int, T>): Int? =
        state.anchorPosition?.let { (it - state.config.initialLoadSize / 2).coerceAtLeast(0) }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (invalid) return@withLock LoadResult.Invalid()
                if (!observing) {
                    observing = true
                    observe(this@LibraryTrackPagingSource)
                }
                val key = (params.key ?: 0).coerceAtLeast(0)
                val limit = if (params is LoadParams.Prepend) minOf(key, params.loadSize) else params.loadSize
                val offset = if (params is LoadParams.Prepend) key - limit else key
                val window = readWindow(offset, limit, count)
                if (invalid) return@withLock LoadResult.Invalid()
                count = window.count
                val end = window.offset + window.data.size
                LoadResult.Page(
                    data = window.data,
                    prevKey = window.offset.takeIf { it > 0 && window.data.isNotEmpty() },
                    nextKey = end.takeIf { it < window.count && window.data.isNotEmpty() },
                    itemsBefore = window.offset,
                    itemsAfter = (window.count - end).coerceAtLeast(0),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                LoadResult.Error(error)
            }
        }
    }
}

internal fun sortedLibraryTrackPagingSource(
    database: EchoLibraryDatabase,
    query: String,
    matchQuery: String?,
    sort: LibraryTrackSortMode,
    filter: LibraryTrackFilter,
): PagingSource<Int, LibraryTrackEntity> {
    val countQuery = LibraryTrackQueryBuilder.boundQuery(query, matchQuery, sort, filter = filter, countOnly = true)
    return LibraryTrackPagingSource(
        readWindow = { requestedOffset, limit, knownCount ->
            database.withTransaction {
                val dao = database.trackDao()
                val count = knownCount ?: dao.queryTrackCount(countQuery)
                val offset = if (knownCount == null) requestedOffset.coerceAtMost((count - 1).coerceAtLeast(0)) else requestedOffset
                val rows = if (count == 0 || limit == 0) emptyList() else dao.queryOrderedTrackWindow(
                    LibraryTrackQueryBuilder.boundQuery(query, matchQuery, sort,
                        idsOnly = true, limit = limit, offset = offset, filter = filter),
                )
                LibraryTrackWindow(rows, count, offset)
            }
        },
        observe = { source ->
            val tracker = database.invalidationTracker
            val reference = WeakReference(source)
            val tables = trackPagingObservedTables(query.isNotBlank() && matchQuery != null, sort, filter)
            val observer = object : InvalidationTracker.Observer(tables.toTypedArray()) {
                override fun onInvalidated(tables: Set<String>) {
                    reference.get()?.invalidate() ?: tracker.removeObserver(this)
                }
            }
            source.registerInvalidatedCallback { tracker.removeObserver(observer) }
            tracker.addObserver(observer)
            if (source.invalid) tracker.removeObserver(observer)
        },
    )
}

internal fun trackPagingObservedTables(usesFts: Boolean, sort: LibraryTrackSortMode, filter: LibraryTrackFilter): List<String> = buildList {
    // Duplicate staging writes are invisible. The repository publishes them through control.
    addAll(listOf("library_tracks", "library_hidden_tracks", "library_duplicate_control"))
    if (usesFts) add("library_tracks_fts")
    if (filter.favoritesOnly) add("library_favorites")
    if (sort in listOf(LibraryTrackSortMode.RecentlyPlayed, LibraryTrackSortMode.FrequentlyPlayed,
            LibraryTrackSortMode.LeastPlayed) || filter.played != LibraryPlayedFilter.Any) add("library_playback_stats")
    if (filter.lyrics != LibraryPresenceFilter.Any) add("library_lyrics_index")
}

/** IN does not preserve order. Restore only this window's ID order, never the full library. */
internal suspend fun LibraryTrackDao.queryOrderedTrackWindow(query: androidx.sqlite.db.SupportSQLiteQuery): List<LibraryTrackEntity> {
    val ids = queryTrackIds(query)
    val rows = ids.chunked(500).flatMap { getTracksByIds(it) }.associateBy { it.id }
    return ids.mapNotNull(rows::get)
}
