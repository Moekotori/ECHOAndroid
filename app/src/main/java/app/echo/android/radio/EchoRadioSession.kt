package app.echo.android.radio

import app.echo.android.data.EchoRadioStore
import app.echo.android.model.radio.EchoRadioStation
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import app.echo.android.playback.EchoPlaybackSurfaceSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Recency follows service playback, including notification and restored sessions. */
internal class EchoRadioSession(val store: EchoRadioStore) {
    private var requested: EchoRadioStation? = null
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    fun request(station: EchoRadioStation) { requested = station }
    fun start(scope: CoroutineScope, surface: StateFlow<EchoPlaybackSurfaceSnapshot>) {
        scope.launch {
            surface.distinctUntilChanged { a, b -> a.mediaId == b.mediaId && a.isPlaying == b.isPlaying }
                .collect { snapshot ->
                    val id = snapshot.mediaId?.takeIf { it.startsWith("radio:") }?.removePrefix("radio:")
                    if (!snapshot.isPlaying || id == null) return@collect
                    val pending = requested?.takeIf { it.id == id }
                    requested = null
                    try {
                        val existing = store.load().firstOrNull { it.id == id }
                        val station = existing ?: pending ?: return@collect
                        store.played(station)
                        mutableRevision.value++
                    } catch (e: CancellationException) { throw e }
                    catch (error: Exception) { EchoErrorLog.record(EchoErrorSource.Library, "Radio history write failed", throwable = error) }
                }
        }
    }
}
