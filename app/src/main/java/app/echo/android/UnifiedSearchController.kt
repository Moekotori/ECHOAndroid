package app.echo.android

import app.echo.android.data.*
import app.echo.android.model.library.EchoUnifiedSearch
import kotlinx.coroutines.*
import androidx.paging.*
import app.echo.android.feature.home.UnifiedSearchItem
import app.echo.android.feature.home.UnifiedSearchSection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import app.echo.android.connect.EchoRemoteClient

internal class UnifiedSearchController(private val repository: EchoLibraryRepository, private val scope: CoroutineScope) {
    private var preview: Pair<String, EchoUnifiedSearch>? = null
    private var pcPreview: Triple<String, String, EchoUnifiedSearch>? = null
    fun cachedPreview(query: String): EchoUnifiedSearch? = preview?.takeIf { it.first == query.trim() }?.second
    fun cachedPcPreview(query: String, endpoint: String): EchoUnifiedSearch? =
        pcPreview?.takeIf { it.first == query.trim() && it.second == endpoint }?.third
    fun cachePcPreview(query: String, endpoint: String, result: EchoUnifiedSearch) {
        pcPreview = Triple(query.trim(), endpoint, result)
    }
    private var pageKey: Triple<UnifiedSearchSection, String, String>? = null
    private var pageJob: Job? = null
    private var pageFlow: Flow<PagingData<UnifiedSearchItem>>? = null
    fun clearSession() {
        pageJob?.cancel(); pageJob = null; pageFlow = null; pageKey = null
        preview = null; pcPreview = null
    }

    fun pages(section: UnifiedSearchSection, query: String, pc: EchoRemoteClient, endpointKey: String): Flow<PagingData<UnifiedSearchItem>> {
        val key = Triple(section, query.trim(), if (section == UnifiedSearchSection.Pc) endpointKey else "")
        if (pageKey == key) return checkNotNull(pageFlow)
        pageJob?.cancel()
        val job = SupervisorJob(scope.coroutineContext[Job])
        pageJob = job
        val source: Flow<PagingData<UnifiedSearchItem>> = when (section) {
            UnifiedSearchSection.Tracks -> repository.pagedTracks(query).map { page -> page.map { UnifiedSearchItem.Track(it.toEchoTrack()) } }
            UnifiedSearchSection.Albums -> repository.pagedSearchAlbums(query).map { page -> page.map { UnifiedSearchItem.Album(it) } }
            UnifiedSearchSection.Artists -> repository.pagedSearchArtists(query).map { page -> page.map { UnifiedSearchItem.Artist(it) } }
            UnifiedSearchSection.Playlists -> repository.pagedSearchPlaylists(query).map { page -> page.map { UnifiedSearchItem.Playlist(it) } }
            UnifiedSearchSection.Moments -> repository.pagedMoments(query).map { page -> page.map { UnifiedSearchItem.Moment(it) } }
            UnifiedSearchSection.Pc -> {
                val endpoint = pc.status.value.endpoint
                Pager(PagingConfig(pageSize = 30, initialLoadSize = 30, maxSize = 180, enablePlaceholders = false)) {
                    PcSearchPagingSource(pc, query, endpoint)
                }.flow
            }
        }
        return source.cachedIn(CoroutineScope(scope.coroutineContext + job)).also { pageKey = key; pageFlow = it }
    }
    suspend fun searchLocal(query: String): EchoUnifiedSearch = supervisorScope {
        if (query.isBlank()) return@supervisorScope EchoUnifiedSearch()
        val local = async(Dispatchers.IO) { repository.searchLocalLibrary(query) }
        val playlists = async { repository.searchUserPlaylists(query) }
        val moments = async { repository.searchMoments(query) }
        var result = EchoUnifiedSearch()
        try { val found = local.await(); result = result.copy(tracks = found.tracks.map { it.toEchoTrack() }, albums = found.albums, artists = found.artists) }
        catch (e: CancellationException) { throw e } catch (_: Exception) { result = result.copy(localUnavailable = true) }
        try { result = result.copy(playlists = playlists.await(), moments = moments.await()) }
        catch (e: CancellationException) { throw e } catch (_: Exception) { result = result.copy(localUnavailable = true) }
        preview = query.trim() to result
        result
    }
}
