package app.echo.android.podcasts

import app.echo.android.data.PodcastStore
import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import app.echo.android.playback.EchoPodcastProgressEvents
import kotlinx.coroutines.*

/** Runs for the playback process, even when there is no Activity or ViewModel. */
internal fun startPodcastProgressRecorder(scope: CoroutineScope, store: PodcastStore) {
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
        EchoPodcastProgressEvents.samples.collect { sample ->
            try { store.progress(sample.id, sample.positionMs, sample.ended, sample.replay) }
            catch (e: CancellationException) { throw e }
            catch (error: Exception) { EchoErrorLog.record(EchoErrorSource.Playback, "Podcast progress write failed", throwable = error) }
        }
    }
}
