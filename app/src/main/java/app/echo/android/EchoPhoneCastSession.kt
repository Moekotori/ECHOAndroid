package app.echo.android

import app.echo.android.connect.EchoLinkCastPlan
import app.echo.android.connect.EchoLinkCastPreparedQueue
import app.echo.android.connect.EchoLinkCastSourceTrack
import app.echo.android.model.connect.EchoRemoteEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class EchoPhoneCastSession(private val session: EchoLinkSession) : EchoPhoneCastHost {
    override val endpoint get() = session.client.status.value.endpoint
    override val supportsAtomicPhoneQueue get() = session.client.status.value.playback.supportsAtomicPhoneQueue

    override suspend fun prepare(tracks: List<EchoLinkCastSourceTrack>, target: EchoRemoteEndpoint): EchoLinkCastPreparedQueue? {
        var candidate: EchoLinkCastPreparedQueue? = null
        try {
            withContext(Dispatchers.IO) {
                val work = coroutineContext
                candidate = session.preparePhoneCast(tracks, target) { work.ensureActive() }
            }
            return candidate
        } catch (error: Throwable) {
            candidate?.let { withContext(NonCancellable + Dispatchers.IO) { session.rollbackPhoneCast(it) } }
            throw error
        }
    }

    override suspend fun send(plan: EchoLinkCastPlan, prepared: EchoLinkCastPreparedQueue?, positionMs: Long) {
        awaitCommand { success, failure ->
            when (plan) {
                is EchoLinkCastPlan.LocalHttp -> session.client.castRemoteQueueToPc(checkNotNull(prepared).items,
                    plan.startIndex, positionMs, onSuccess = success, onFailure = failure)
                is EchoLinkCastPlan.HandoffPcLibrary -> session.client.handoffPhoneQueueToPc(plan.tracks,
                    plan.startIndex, positionMs, onSuccess = success, onFailure = failure)
                is EchoLinkCastPlan.Blocked -> error("Cannot send a blocked cast")
            }
        }
    }

    override suspend fun commit(prepared: EchoLinkCastPreparedQueue?, target: EchoRemoteEndpoint) {
        session.completePhoneCast(prepared)
    }

    override suspend fun rollback(prepared: EchoLinkCastPreparedQueue) {
        withContext(Dispatchers.IO) { session.rollbackPhoneCast(prepared) }
    }

    override fun activate(target: EchoRemoteEndpoint) { session.markCastStarted(target.name) }

    private suspend fun awaitCommand(send: (success: () -> Unit, failure: (Throwable?) -> Unit) -> Job?) =
        suspendCancellableCoroutine<Unit> { continuation ->
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
