package app.echo.android.radio

import app.echo.android.data.EchoRadioStore
import app.echo.android.data.RadioBrowserClient
import app.echo.android.data.RadioBrowserPolicy
import app.echo.android.model.radio.EchoRadioDirectorySearch
import app.echo.android.model.radio.EchoRadioStation
import app.echo.android.model.radio.RadioDirectoryQuery
import app.echo.android.feature.radio.RadioDiscoveryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

class EchoRadioController(
    private val store: EchoRadioStore,
    private val directory: RadioBrowserClient,
    private val scope: CoroutineScope,
) {
    private val mutableStations = MutableStateFlow<List<EchoRadioStation>>(emptyList())
    val stations = mutableStations.asStateFlow()
    private val mutableVisible = MutableStateFlow<List<EchoRadioStation>>(emptyList())
    val visibleStations = mutableVisible.asStateFlow()
    private val mutableRecent = MutableStateFlow<List<EchoRadioStation>>(emptyList())
    val recent = mutableRecent.asStateFlow()
    private val mutableDiscovery = MutableStateFlow(RadioDiscoveryState())
    val discovery = mutableDiscovery.asStateFlow()
    private var browseJob: Job? = null
    private var filterJob: Job? = null
    private var filterQuery = ""
    private var favoritesOnly = false
    private var directoryOpen = false
    private var foreground = true
    private var importProbeJob: Job? = null
    private val inspector = app.echo.android.data.RadioStreamInspector()
    private val mutex = Mutex()
    private val mutableLoadFailed = MutableStateFlow(false)
    val loadFailed = mutableLoadFailed.asStateFlow()
    private val mutableDirectory = MutableStateFlow(EchoRadioDirectorySearch())
    val directorySearch = mutableDirectory.asStateFlow()
    private var directoryJob: Job? = null

    init { reload() }

    fun filterSaved(query: String, favorites: Boolean) {
        filterQuery = query.take(128); favoritesOnly = favorites
        filterJob?.cancel()
        filterJob = scope.launch {
            val source = mutableStations.value
            val filtered = withContext(Dispatchers.Default) {
                val term = java.text.Normalizer.normalize(query.trim(), java.text.Normalizer.Form.NFKC).lowercase()
                source.filter { station -> (!favorites || station.favorite) &&
                    java.text.Normalizer.normalize(listOf(station.name, station.genre, station.description, station.countryCode, station.language).joinToString(" "),
                        java.text.Normalizer.Form.NFKC).lowercase().contains(term) }
                    .sortedWith(compareByDescending<EchoRadioStation> { it.favorite }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            }
            mutableVisible.value = filtered
            mutableRecent.value = withContext(Dispatchers.Default) { source.filter { it.lastPlayedMs > 0 }.sortedByDescending { it.lastPlayedMs }.take(8) }
        }
    }

    private fun changed(stations: List<EchoRadioStation>) { mutableStations.value = stations; filterSaved(filterQuery, favoritesOnly) }

    fun browse(query: RadioDirectoryQuery) {
        directoryOpen = true
        browseJob?.cancel()
        mutableDiscovery.value = RadioDiscoveryState(query = query, loading = foreground)
        if (!foreground) return
        browseJob = scope.launch {
            try {
                val page = directory.browse(query)
                mutableDiscovery.value = RadioDiscoveryState(query, page.stations, page.hasNext)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableDiscovery.value = RadioDiscoveryState(query, failed = true) }
        }
    }
    fun closeDirectory() { directoryOpen = false; browseJob?.cancel(); mutableDiscovery.value = RadioDiscoveryState(query = mutableDiscovery.value.query) }
    fun foreground(active: Boolean) {
        foreground = active
        if (!active) { browseJob?.cancel(); importProbeJob?.cancel(); mutableDiscovery.update { it.copy(rows = emptyList(), loading = false) } }
        else { reload(); if (directoryOpen) browse(mutableDiscovery.value.query) }
    }

    suspend fun saveStation(station: EchoRadioStation) = mutex.withLock {
        val existing = mutableStations.value.firstOrNull { it.id == station.id || it.url == station.url }
        val saved = if (existing != null && existing.id != station.id) existing.copy(artworkUrl = existing.artworkUrl ?: station.artworkUrl) else station
        changed(store.save(saved))
    }
    suspend fun favorite(id: String) = mutex.withLock { changed(store.favorite(id)) }
    suspend fun clear() = mutex.withLock { changed(store.clear()) }
    suspend fun importText(text: String) {
        val entries = withContext(Dispatchers.Default) { app.echo.android.data.RadioPlaylistCodec.parse(text) }
        require(entries.isNotEmpty())
        importStations(entries)
    }
    suspend fun importStations(entries: List<EchoRadioStation>) {
        val added = mutex.withLock {
            val existing = mutableStations.value.mapTo(HashSet()) { it.url }
            changed(store.importStations(entries))
            mutableStations.value.filter { it.url !in existing }.take(10)
        }
        importProbeJob?.cancel()
        importProbeJob = scope.launch {
            for (station in added) {
                try {
                    val facts = inspector.inspect(station.url)
                    mutex.withLock {
                        changed(store.enrich(station.id, station.url, facts))
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* Imported stations remain available when enrichment fails. */ }
            }
        }
    }
    suspend fun exportText(): String = withContext(Dispatchers.Default) { app.echo.android.data.RadioPlaylistCodec.export(mutableStations.value) }
    suspend fun exportPlaylist(): String = withContext(Dispatchers.Default) { app.echo.android.data.RadioPlaylistCodec.exportM3u(mutableStations.value) }
    suspend fun probe(url: String) = inspector.inspect(url)

    fun reload() {
        scope.launch {
            mutex.withLock {
                try {
                    changed(store.load())
                    mutableLoadFailed.value = false
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableLoadFailed.value = true
                }
            }
        }
    }

    suspend fun save(id: String?, name: String, url: String) = mutex.withLock {
        val previous = mutableStations.value.firstOrNull { it.id == id }
        val saved = store.save((previous ?: EchoRadioStation(id ?: UUID.randomUUID().toString(), name.trim(), url.trim()))
            .copy(name = name.trim(), url = url.trim()))
        changed(saved)
        mutableLoadFailed.value = false
        val savedUrl = url.trim()
        mutableDirectory.update { current ->
            current.copy(stations = current.stations.filterNot { it.url == savedUrl })
        }
    }

    suspend fun delete(id: String) = mutex.withLock {
        changed(store.delete(id))
        mutableLoadFailed.value = false
    }

    fun searchDirectory(query: String) {
        directoryJob?.cancel()
        val normalized = RadioBrowserPolicy.normalizedQuery(query)
        if (normalized == null) {
            mutableDirectory.value = EchoRadioDirectorySearch()
            return
        }
        directoryJob = scope.launch {
            delay(RadioBrowserPolicy.DebounceMs)
            mutableDirectory.value = EchoRadioDirectorySearch(query = normalized, loading = true)
            try {
                val savedUrls = mutableStations.value.mapTo(HashSet()) { it.url }
                val stations = directory.search(normalized).filterNot { it.url in savedUrls }
                mutableDirectory.value = EchoRadioDirectorySearch(query = normalized, stations = stations)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableDirectory.value = EchoRadioDirectorySearch(query = normalized, failed = true)
            }
        }
    }
}
