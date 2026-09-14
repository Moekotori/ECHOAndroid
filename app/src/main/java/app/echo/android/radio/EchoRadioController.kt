package app.echo.android.radio

import app.echo.android.data.EchoRadioStore
import app.echo.android.data.RadioBrowserClient
import app.echo.android.data.RadioBrowserPolicy
import app.echo.android.model.radio.EchoRadioDirectorySearch
import app.echo.android.model.radio.EchoRadioStation
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
import java.util.UUID

class EchoRadioController(
    private val store: EchoRadioStore,
    private val directory: RadioBrowserClient,
    private val scope: CoroutineScope,
) {
    private val mutableStations = MutableStateFlow<List<EchoRadioStation>>(emptyList())
    val stations = mutableStations.asStateFlow()
    private val mutex = Mutex()
    private val mutableLoadFailed = MutableStateFlow(false)
    val loadFailed = mutableLoadFailed.asStateFlow()
    private val mutableDirectory = MutableStateFlow(EchoRadioDirectorySearch())
    val directorySearch = mutableDirectory.asStateFlow()
    private var directoryJob: Job? = null

    init { reload() }

    fun reload() {
        scope.launch {
            mutex.withLock {
                try {
                    mutableStations.value = store.load()
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
        val saved = store.save(EchoRadioStation(id ?: UUID.randomUUID().toString(), name.trim(), url.trim()))
        mutableStations.value = saved
        mutableLoadFailed.value = false
        val savedUrl = url.trim()
        mutableDirectory.update { current ->
            current.copy(stations = current.stations.filterNot { it.url == savedUrl })
        }
    }

    suspend fun delete(id: String) = mutex.withLock {
        mutableStations.value = store.delete(id)
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
