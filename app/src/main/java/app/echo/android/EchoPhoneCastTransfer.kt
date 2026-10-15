package app.echo.android

import app.echo.android.connect.EchoLinkCastBlockReason
import app.echo.android.connect.EchoLinkCastPlan
import app.echo.android.connect.EchoLinkCastPreparedQueue
import app.echo.android.model.connect.EchoRemoteEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Owns one preparation/acknowledgement transaction, independently of activity recreation. */
internal class EchoPhoneCastTransfer(
    private val session: EchoLinkSession,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null
    private var generation = 0L

    fun cancel() { job?.cancel() }

    fun start(
        buildPlan: suspend () -> EchoLinkCastPlan,
        positionMs: Long,
        onBlocked: (EchoLinkCastBlockReason) -> Unit,
        onFailure: (Throwable) -> Unit,
        onCancelled: () -> Unit,
        onSuccess: () -> Unit,
    ) {
        val previous = job
        previous?.cancel()
        val target = session.client.status.value.endpoint
        val request = ++generation
        job = scope.launch {
            var prepared: EchoLinkCastPreparedQueue? = null
            try {
                // Wait for the previous request's rollback before publishing another candidate.
                previous?.join()
                val plan = withContext(Dispatchers.Default) { buildPlan() }
                if (plan is EchoLinkCastPlan.Blocked) {
                    onBlocked(plan.reason)
                    return@launch
                }
                checkTarget(target)
                when (plan) {
                    is EchoLinkCastPlan.LocalHttp -> {
                        withContext(Dispatchers.IO) {
                            val work = coroutineContext
                            prepared = session.preparePhoneCast(plan.tracks) { work.ensureActive() }
                                ?: error("no_lan")
                        }
                        checkTarget(target)
                        val candidate = checkNotNull(prepared)
                        awaitCommand { success, failure ->
                            session.client.castRemoteQueueToPc(candidate.items, plan.startIndex, positionMs,
                                onSuccess = success, onFailure = failure)
                        }
                        checkTarget(target)
                        withContext(Dispatchers.IO) { check(session.castServer.commit(candidate)) }
                        prepared = null
                    }
                    is EchoLinkCastPlan.HandoffPcLibrary -> {
                        awaitCommand { success, failure ->
                            session.client.handoffPhoneQueueToPc(plan.tracks, plan.startIndex, positionMs,
                                onSuccess = success, onFailure = failure)
                        }
                        checkTarget(target)
                        session.releaseLocalCast()
                    }
                    is EchoLinkCastPlan.Blocked -> Unit
                }
                onSuccess()
            } catch (cancelled: CancellationException) {
                if (request == generation) onCancelled()
                throw cancelled
            } catch (error: Exception) {
                // Finish rollback before allowing a retry through the UI.
                prepared?.let { candidate ->
                    withContext(NonCancellable + Dispatchers.IO) { session.rollbackPhoneCast(candidate) }
                    prepared = null
                }
                onFailure(error)
            } finally {
                prepared?.let { candidate ->
                    withContext(NonCancellable + Dispatchers.IO) { session.rollbackPhoneCast(candidate) }
                }
            }
        }
    }

    private fun checkTarget(target: EchoRemoteEndpoint?) {
        check(target != null && session.client.status.value.endpoint == target) { "Cast destination changed" }
    }

    private suspend fun awaitCommand(
        send: (success: () -> Unit, failure: (Throwable?) -> Unit) -> Job?,
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        val command = send(
            { if (continuation.isActive) continuation.resume(Unit) },
            { error -> if (continuation.isActive) continuation.resumeWithException(error ?: IllegalStateException("Cast failed")) },
        )
        continuation.invokeOnCancellation { command?.cancel() }
        command?.invokeOnCompletion { error ->
            if (continuation.isActive) continuation.cancel(error ?: CancellationException("Cast request expired"))
        }
    }
}
