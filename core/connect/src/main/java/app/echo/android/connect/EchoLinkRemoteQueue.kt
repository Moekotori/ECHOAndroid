package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteCommand
import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoRemotePlaybackQueue
import app.echo.android.model.connect.EchoRemoteQueueState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Demand-driven browsing: no timers, and one request or queue mutation at a time. */
internal class EchoLinkRemoteQueue(
    private val scope: CoroutineScope,
    private val transport: EchoLinkTransport,
    private val endpoint: () -> EchoRemoteEndpoint?,
    private val fallback: () -> EchoRemotePlaybackQueue,
    private val errorMessage: (Throwable) -> String,
) {
    private val mutableState = MutableStateFlow(EchoRemoteQueueState())
    val state = mutableState.asStateFlow()
    fun confirmSelection(queueId: String) { mutableState.value = state.value.copy(currentQueueId = queueId) }
    private var job: Job? = null
    private var generation = 0L
    private var watching = false
    private var visibleAnchor = 0
    private var pendingIdentity: Pair<Long, String?>? = null
    private var lastRequestedRevision: Long? = null

    fun startWatching() { watching = true }
    fun stopWatching() { watching = false; visibleAnchor = 0; cancel(clear = true) }
    fun setVisibleAnchor(index: Int) { visibleAnchor = index.coerceAtLeast(0) }
    fun observe(revision: Long?, currentQueueId: String?) {
        if (!watching || state.value.unsupported || revision == null) return
        if (revision != 0L && (revision < (state.value.revision ?: 0L) || revision < (pendingIdentity?.first ?: 0L))) return
        if (job?.isActive == true || state.value.isMoving) {
            pendingIdentity = revision to currentQueueId
            return
        }
        if (revision != state.value.revision) {
            if (revision == lastRequestedRevision && state.value.error != null) return
            lastRequestedRevision = revision
            reloadWindow()
        } else if (currentQueueId != state.value.currentQueueId) {
            mutableState.value = state.value.copy(currentQueueId = currentQueueId)
        }
    }
    private fun finishRequest(request: Long) {
        if (request != generation) return
        job = null
        val pending = pendingIdentity
        pendingIdentity = null
        pending?.let { observe(it.first, it.second) }
    }
    private fun reloadWindow(keepError: String? = null) {
        cancel()
        load(visibleAnchor / 100 + 1, replaceWindow = true, requestError = keepError)
    }

    fun cancel(clear: Boolean = false) {
        generation++
        pendingIdentity = null
        if (clear) lastRequestedRevision = null
        job?.cancel(); job = null
        mutableState.value = if (clear) EchoRemoteQueueState() else mutableState.value.copy(
            isLoading = false, isLoadingMore = false, isMoving = false)
    }
    fun refresh(keepError: String? = null) { cancel(); load(1, requestError = keepError) }
    fun loadMore() {
        val current = state.value
        if (job?.isActive == true || current.unsupported || current.offset + current.items.size >= current.totalCount) return
        load((current.offset + current.items.size) / 100 + 1)
    }
    fun loadPrevious() {
        val current = state.value
        if (job?.isActive == true || current.unsupported || current.offset == 0) return
        load(current.offset / 100, prepend = true)
    }
    private fun load(page: Int, prepend: Boolean = false, requestError: String? = null, replaceWindow: Boolean = false) {
        val target = endpoint() ?: return
        val request = ++generation
        mutableState.value = state.value.copy(isLoading = replaceWindow || (page == 1 && !prepend),
            isLoadingMore = !replaceWindow && (page > 1 || prepend), error = requestError)
        job = scope.launch {
            try {
                val result = transport.fetchPlaybackQueue(target, page)
                if (request != generation || !EchoLinkRequestPolicy.isSameEndpoint(endpoint(), target)) return@launch
                val current = state.value
                if (!replaceWindow && (page > 1 || prepend) && result.revision != current.revision) {
                    reloadWindow(errorMessage(EchoLinkHttpException("playback_queue_session_conflict"))); return@launch
                }
                if (replaceWindow && result.items.isEmpty() && result.totalCount > 0 && page > 1) {
                    visibleAnchor = result.totalCount - 1
                    load(visibleAnchor / 100 + 1, replaceWindow = true, requestError = requestError)
                    return@launch
                }
                if (page > 1 && result.items.isEmpty() && current.items.size < result.totalCount) {
                    throw EchoLinkHttpException("PC returned an incomplete queue page")
                }
                val extending = !replaceWindow && (page > 1 || prepend)
                val combined = if (!extending) result.items else if (prepend) result.items + current.items else current.items + result.items
                val dropped = (combined.size - 1000).coerceAtLeast(0)
                mutableState.value = result.copy(
                    items = if (prepend) combined.take(1000) else combined.drop(dropped),
                    offset = if (replaceWindow || prepend) (page - 1) * 100 else if (extending) current.offset + dropped else 0,
                    error = requestError,
                )
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (request != generation) return@launch
                val oldPc = (error as? EchoLinkHttpException)?.statusCode == 404
                val preview = fallback()
                mutableState.value = if (oldPc) EchoRemoteQueueState(items = preview.items,
                    totalCount = preview.totalCount, unsupported = true, error = errorMessage(EchoLinkHttpException("unknown_command")))
                else state.value.copy(isLoading = false, isLoadingMore = false, error = errorMessage(error))
            } finally { finishRequest(request) }
        }
    }
    fun move(queueId: String, toIndex: Int) {
        if (state.value.isMoving) return
        cancel()
        val target = endpoint() ?: return
        val current = state.value
        val revision = current.revision ?: return
        if (current.unsupported || toIndex !in 0 until current.totalCount) return
        val request = ++generation
        mutableState.value = current.copy(isMoving = true, error = null)
        job = scope.launch {
            try {
                val response = transport.sendCommand(target, EchoRemoteCommand.QueueMove(queueId, toIndex, revision))
                if (request != generation || !EchoLinkRequestPolicy.isSameEndpoint(endpoint(), target)) return@launch
                val nextRevision = response?.playback?.queue?.revision
                val from = current.items.indexOfFirst { it.queueId == queueId }
                val relativeTarget = toIndex - current.offset
                if (nextRevision == null || from < 0 || relativeTarget !in current.items.indices) {
                    refresh()
                } else {
                    val reordered = current.items.toMutableList()
                    reordered.add(relativeTarget, reordered.removeAt(from))
                    mutableState.value = current.copy(items = reordered.mapIndexed { index, item -> item.copy(queueIndex = index + current.offset) },
                        revision = nextRevision, isMoving = false, error = null,
                        currentQueueId = if (response.playback.queueIdentityAvailable) response.playback.queue.currentQueueId else current.currentQueueId)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (request == generation) {
                    mutableState.value = state.value.copy(isMoving = false, error = errorMessage(error))
                    if ((error as? EchoLinkHttpException)?.statusCode == 409) reloadWindow(errorMessage(error))
                }
            } finally { finishRequest(request) }
        }
    }
}
