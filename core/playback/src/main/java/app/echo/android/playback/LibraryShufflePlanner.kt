package app.echo.android.playback

import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import app.echo.android.model.library.EchoTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One outstanding draw per occurrence. An exhausted source waits for an index change. */
internal class LibraryShufflePlanner(private val scope: CoroutineScope, private val catalog: () -> EchoPlaybackCatalog) {
    private var requestKey: String? = null
    private var job: Job? = null

    fun cancel() {
        requestKey = null
        job?.cancel()
        job = null
    }

    fun request(key: String, currentId: String, recent: List<String>, onTrack: (EchoTrack) -> Unit) {
        if (requestKey == key) return
        cancel()
        requestKey = key
        job = scope.launch {
            val source = catalog()
            val track = source.localShuffleCatalogChanges().map {
                sample(source, currentId, recent)
            }.filterNotNull().firstOrNull()
            if (requestKey == key && track != null) onTrack(track)
        }
    }

    private suspend fun sample(source: EchoPlaybackCatalog, currentId: String, recent: List<String>): EchoTrack? {
        repeat(3) { attempt ->
            try {
                return withContext(Dispatchers.IO) { source.shuffleLocalTrack(currentId, recent) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (attempt == 2) {
                    EchoErrorLog.record(EchoErrorSource.Playback, "Unable to sample the local library for shuffle.", throwable = error)
                } else delay(250L * (attempt + 1))
            }
        }
        return null
    }
}
