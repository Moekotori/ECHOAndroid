package app.echo.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Main-thread owned, bounded authorization queue. Results can consume only their own head. */
internal class PendingTagWriteQueue<T>(
    private val capacity: Int = 16,
    private val requestIdOf: (T) -> Long,
) {
    private val requests = ArrayDeque<T>()
    private val _pending = MutableStateFlow<T?>(null)
    val pending = _pending.asStateFlow()

    fun enqueue(request: T): Boolean {
        if (requests.size >= capacity) return false
        requests.addLast(request)
        _pending.value = requests.first()
        return true
    }

    fun remove(requestId: Long) {
        requests.removeAll { requestIdOf(it) == requestId }
        _pending.value = requests.firstOrNull()
    }

    fun take(requestId: Long): T? {
        val head = requests.firstOrNull() ?: return null
        if (requestIdOf(head) != requestId) return null
        requests.removeFirst()
        _pending.value = requests.firstOrNull()
        return head
    }
}

/** What the consent-dialog effect should do for the current tag-write queue state. */
internal enum class TagWriteConsentLaunchDecision { Idle, DialogInFlight, ClearLaunchedId, LaunchDialog }

/**
 * A launched consent id is valid only while it names the pending head. The queue dies with
 * the ViewModel, so an id restored across process death — or left behind by a request that
 * was cancelled mid-dialog — must never suppress the next consent dialog, or every later
 * tag write would hang on the authorization mutex.
 */
internal fun tagWriteConsentLaunchDecision(pendingRequestId: Long?, launchedRequestId: Long?): TagWriteConsentLaunchDecision =
    when {
        pendingRequestId == null && launchedRequestId == null -> TagWriteConsentLaunchDecision.Idle
        pendingRequestId == null -> TagWriteConsentLaunchDecision.ClearLaunchedId
        launchedRequestId == pendingRequestId -> TagWriteConsentLaunchDecision.DialogInFlight
        else -> TagWriteConsentLaunchDecision.LaunchDialog
    }
