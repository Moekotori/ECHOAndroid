package app.echo.android.connect

import app.echo.android.model.connect.EchoRemoteEndpoint
import app.echo.android.model.connect.EchoRemoteMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Owns one event subscription; transport callbacks never mutate client state. */
internal class EchoLinkEventSession(
    private val scope: CoroutineScope,
    private val transport: EchoLinkTransport,
    private val retryDelayMs: Long = 1_000L,
    private val onConnected: () -> Unit,
    private val onEvent: (EchoRemoteMessage) -> Unit,
    private val onFailure: (Throwable?) -> Boolean,
) {
    private var job: Job? = null

    fun start(endpoint: EchoRemoteEndpoint) {
        stop()
        job = scope.launch {
            var failures = 0
            while (isActive) {
                // SSE supplies complete snapshots. Keep only the newest pending one.
                val snapshots = Channel<EchoRemoteMessage>(Channel.CONFLATED)
                var subscription: EchoLinkEventSubscription? = null
                var firstSnapshotAtNanos: Long? = null
                val failure = try {
                    val ticket = transport.createEventTicket(endpoint)
                    subscription = transport.subscribeEvents(
                        endpoint, ticket,
                        onEvent = { snapshots.trySend(it) },
                        onClosed = { snapshots.close(it) },
                    )
                    for (message in snapshots) {
                        if (firstSnapshotAtNanos == null) {
                            firstSnapshotAtNanos = System.nanoTime()
                            onConnected()
                        }
                        onEvent(message)
                    }
                    null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    error
                } finally {
                    snapshots.cancel()
                    subscription?.cancel()
                }
                if (!isActive || !onFailure(failure)) break
                // A server that sends one snapshot then closes must not retry forever.
                if (firstSnapshotAtNanos?.let { System.nanoTime() - it >= HealthySessionNanos } == true) {
                    failures = 0
                }
                failures += 1
                val code = (failure as? EchoLinkHttpException)?.statusCode
                if (failures > MaxRetries || code in PermanentFailures) break
                delay((retryDelayMs * (1L shl (failures - 1))).coerceAtMost(30_000L))
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val MaxRetries = 3
        const val HealthySessionNanos = 30_000_000_000L
        val PermanentFailures = setOf(400, 401, 403, 404, 405, 501)
    }
}
