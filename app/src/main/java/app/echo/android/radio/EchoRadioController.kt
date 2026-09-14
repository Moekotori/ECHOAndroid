package app.echo.android.radio

import app.echo.android.data.EchoRadioStore
import app.echo.android.model.radio.EchoRadioStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class EchoRadioController(private val store: EchoRadioStore, private val scope: CoroutineScope) {
    private val mutableStations = MutableStateFlow<List<EchoRadioStation>>(emptyList())
    val stations = mutableStations.asStateFlow()
    private val mutex = Mutex()
    private val mutableLoadFailed = MutableStateFlow(false)
    val loadFailed = mutableLoadFailed.asStateFlow()

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
        mutableStations.value = store.save(EchoRadioStation(id ?: UUID.randomUUID().toString(), name.trim(), url.trim()))
        mutableLoadFailed.value = false
    }

    suspend fun delete(id: String) = mutex.withLock {
        mutableStations.value = store.delete(id)
        mutableLoadFailed.value = false
    }
}
