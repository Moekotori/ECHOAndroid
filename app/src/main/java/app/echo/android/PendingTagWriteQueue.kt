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
