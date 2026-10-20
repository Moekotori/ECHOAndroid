package app.echo.android

import app.echo.android.data.EchoLibraryRepository
import app.echo.android.data.resolvePreferredTrackVersions
import app.echo.android.model.library.EchoTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One replacement request owns the queue selection until a newer user action supersedes it. */
internal class PreferredPlaybackSelection(
    private val scope: CoroutineScope,
    private val repository: EchoLibraryRepository,
    private val readiness: suspend () -> (EchoTrack) -> Boolean,
    private val onFailure: (Exception) -> Unit,
) {
    private var selection: Job? = null
    private var generation = 0L
    private val additions = Mutex()

    fun invalidate() { generation++; selection?.cancel(); selection = null }
    fun launch(block: suspend () -> Unit): Job {
        invalidate()
        val job = scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { onFailure(error) }
        }
        selection = job
        return job
    }
    fun generationToken(): Long = generation
    fun isCurrent(token: Long): Boolean = token == generation

    suspend fun resolve(tracks: List<EchoTrack>): List<EchoTrack> {
        val resolved = try { repository.resolvePreferredTrackVersions(tracks, readiness()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { onFailure(error); tracks }
        currentCoroutineContext().ensureActive()
        return resolved
    }

    fun append(load: suspend () -> List<EchoTrack>, apply: (List<EchoTrack>) -> Unit) {
        val requestedGeneration = generation
        scope.launch {
            additions.withLock {
                try {
                    val resolved = resolve(load())
                    if (requestedGeneration == generation) apply(resolved)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { onFailure(error) }
            }
        }
    }
}
