package app.echo.android.podcasts

import app.echo.android.data.PodcastRepository
import app.echo.android.feature.podcasts.PodcastScreenState
import app.echo.android.model.podcast.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class PodcastController(private val repository: PodcastRepository, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(PodcastScreenState())
    val state = mutable.asStateFlow()
    private var queryJob: Job? = null
    private var operation: Job? = null
    private var opened = false
    private var queryGeneration = 0L
    private var operationGeneration = 0L
    private var failedRefreshId: String? = null

    fun open() { opened = true; retry() }
    fun close() {
        opened = false; queryGeneration++; operationGeneration++
        queryJob?.cancel(); operation?.cancel()
        mutable.update { it.copy(episodes = PodcastEpisodePage(), loading = false, busy = false) }
    }

    fun retry() {
        if (mutable.value.failed && failedRefreshId != null && failedRefreshId == mutable.value.selected?.id) { refresh(); return }
        queryJob?.cancel()
        val generation = ++queryGeneration
        queryJob = scope.launch {
            mutable.update { it.copy(loading = true, failed = false) }
            try {
                reloadFeeds()
                loadPage(mutable.value.episodes.page)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(failed = true) } }
            finally { if (generation == queryGeneration) mutable.update { it.copy(loading = false) } }
        }
    }

    suspend fun subscribe(url: String) {
        check(!mutable.value.busy)
        mutable.update { it.copy(busy = true) }
        try {
            val feed = repository.subscribe(url)
            mutable.update { it.copy(feedQuery = "") }
            reloadFeeds()
            select(feed)
        } finally { mutable.update { it.copy(busy = false) } }
    }

    fun select(feed: EchoPodcastFeed?) {
        queryJob?.cancel()
        queryGeneration++
        failedRefreshId = null
        mutable.update { it.copy(selected = feed, episodeQuery = "", filter = PodcastListenFilter.All,
            episodes = PodcastEpisodePage(), failed = false, loading = false) }
        if (feed != null) query(0)
    }

    fun searchFeeds(query: String) {
        val term = query.take(200)
        mutable.update { current -> current.copy(feedQuery = term, visibleFeeds = current.feeds.filter {
            it.title.contains(term, true) || it.author.contains(term, true)
        }) }
    }
    fun searchEpisodes(query: String) { mutable.update { it.copy(episodeQuery = query.take(200)) }; query(0, debounce = true) }
    fun filter(filter: PodcastListenFilter) { mutable.update { it.copy(filter = filter) }; query(0) }
    fun page(page: Int) = query(page)

    private fun query(page: Int, debounce: Boolean = false) {
        queryJob?.cancel()
        val generation = ++queryGeneration
        mutable.update { it.copy(episodes = PodcastEpisodePage(page = page), loading = true, failed = false) }
        queryJob = scope.launch {
            try { if (debounce) delay(350); loadPage(page) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(failed = true) } }
            finally { if (generation == queryGeneration) mutable.update { it.copy(loading = false) } }
        }
    }

    private suspend fun loadPage(page: Int) {
        val request = mutable.value
        val generation = queryGeneration
        val feed = request.selected ?: return
        val result = repository.store.page(feed.id, request.episodeQuery, request.filter, page)
        currentCoroutineContext().ensureActive()
        if (opened && generation == queryGeneration && mutable.value.selected?.id == feed.id && mutable.value.episodeQuery == request.episodeQuery && mutable.value.filter == request.filter) {
            mutable.update { it.copy(episodes = result) }
        }
    }

    fun refresh() = mutate {
        val selected = mutable.value.selected ?: return@mutate
        failedRefreshId = selected.id
        repository.refresh(selected)
        failedRefreshId = null
        reloadFeeds()
        if (opened && mutable.value.selected?.id == selected.id) loadPage(mutable.value.episodes.page)
    }
    fun unfollow() = mutate {
        val selected = mutable.value.selected ?: return@mutate
        repository.store.unfollow(selected.id)
        if (mutable.value.selected?.id == selected.id) select(null)
        reloadFeeds()
    }
    fun markPlayed(episode: EchoPodcastEpisode) = mutate {
        repository.store.markPlayed(episode.id, !episode.played)
        if (opened && mutable.value.selected?.id == episode.feedId) query(mutable.value.episodes.page)
    }

    private fun mutate(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, failed = false) }
        val generation = ++operationGeneration
        operation = scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(failed = true) } }
            finally { if (generation == operationGeneration) mutable.update { it.copy(busy = false) } }
        }
    }
    private suspend fun reloadFeeds() {
        val feeds = repository.store.feeds()
        currentCoroutineContext().ensureActive()
        mutable.update { current -> current.copy(feeds = feeds,
            visibleFeeds = feeds.filter { it.title.contains(current.feedQuery, true) || it.author.contains(current.feedQuery, true) },
            selected = feeds.firstOrNull { it.id == current.selected?.id }) }
    }
}
