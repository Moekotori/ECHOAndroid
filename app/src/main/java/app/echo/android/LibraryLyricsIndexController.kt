package app.echo.android

import app.echo.android.model.library.LibraryLyricsIndexState
import app.echo.android.model.library.LibraryLyricsInspection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One explicit inspection shared by the filter panel and the existing maintenance screen. */
internal class LibraryLyricsIndexController(
    private val scope: CoroutineScope,
    private val readSummary: suspend () -> LibraryLyricsInspection,
    private val inspectRows: suspend ((LibraryLyricsInspection) -> Unit) -> Unit,
) {
    private val mutableState = MutableStateFlow(LibraryLyricsIndexState())
    private var task: Deferred<Unit>? = null
    private var generation = 0L
    private var summaryLoaded = false
    val state = mutableState.onStart {
        if (!summaryLoaded && !mutableState.value.isRunning) refreshSummary(generation)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000L), LibraryLyricsIndexState())

    fun start() { scope.launch(Dispatchers.Main.immediate) { ensureTask() } }

    fun cancel() {
        val cancelled = task
        cancelled?.cancel()
        task = null
        summaryLoaded = false
        mutableState.value = mutableState.value.copy(isRunning = false)
        val token = generation
        scope.launch(Dispatchers.Main.immediate) {
            cancelled?.join()
            if (generation == token && !mutableState.value.isRunning) refreshSummary(token)
        }
    }

    suspend fun inspect(report: (LibraryLyricsInspection) -> Unit) = coroutineScope {
        val (work, owner) = withContext(Dispatchers.Main.immediate) {
            val active = task?.takeIf { it.isActive }
            (active ?: ensureTask()) to (active == null)
        }
        val updates = launch(Dispatchers.Main.immediate) { state.collect { report(it.progress) } }
        try {
            work.await()
            withContext(Dispatchers.Main.immediate) { report(mutableState.value.progress) }
        } catch (cancelled: CancellationException) {
            // Preserve the maintenance screen's cancellation ownership when it starts a run.
            if (owner) withContext(NonCancellable + Dispatchers.Main.immediate) {
                if (task === work) cancel()
            }
            throw cancelled
        } finally { updates.cancel() }
    }

    private fun ensureTask(): Deferred<Unit> {
        task?.takeIf { it.isActive }?.let { return it }
        val token = ++generation
        summaryLoaded = true
        mutableState.value = LibraryLyricsIndexState(isRunning = true)
        val work = scope.async(start = CoroutineStart.LAZY) {
            try {
                inspectRows { progress ->
                    if (generation == token) mutableState.value = mutableState.value.copy(progress = progress)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == token) mutableState.value = mutableState.value.copy(failed = true)
                throw error
            } finally {
                if (generation == token) mutableState.value = mutableState.value.copy(isRunning = false)
            }
        }
        task = work
        work.start()
        return work
    }

    private suspend fun refreshSummary(token: Long) {
        try {
            val progress = readSummary()
            if (generation == token && !mutableState.value.isRunning) {
                summaryLoaded = true
                mutableState.value = mutableState.value.copy(progress = progress, failed = false)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (generation == token) mutableState.value = mutableState.value.copy(failed = true)
        }
    }
}
