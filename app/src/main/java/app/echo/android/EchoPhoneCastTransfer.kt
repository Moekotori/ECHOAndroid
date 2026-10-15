package app.echo.android

import app.echo.android.connect.EchoLinkAtomicQueueRequiredException
import app.echo.android.connect.EchoLinkCastBlockReason
import app.echo.android.connect.EchoLinkCastPlan
import app.echo.android.connect.EchoLinkCastPolicy
import app.echo.android.connect.EchoLinkCastPreparedQueue
import app.echo.android.connect.EchoLinkCastSourceTrack
import app.echo.android.model.connect.EchoRemoteEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class EchoPhoneCastIssue {
    NoLan, ClippedTrack, UnsupportedSource, AtomicPhoneQueueRequired, AtomicPcQueueUnavailable, SourceChanged, Failed,
}

internal data class EchoPhoneCastTransferState(
    val isBusy: Boolean = false,
    val targetAddress: String? = null,
    val issue: EchoPhoneCastIssue? = null,
)

/** Android/service boundary; the transaction itself can be verified without an Activity. */
internal interface EchoPhoneCastHost {
    val endpoint: EchoRemoteEndpoint?
    val supportsAtomicPhoneQueue: Boolean
    suspend fun prepare(tracks: List<EchoLinkCastSourceTrack>, target: EchoRemoteEndpoint): EchoLinkCastPreparedQueue?
    suspend fun send(plan: EchoLinkCastPlan, prepared: EchoLinkCastPreparedQueue?, positionMs: Long)
    suspend fun commit(prepared: EchoLinkCastPreparedQueue?, target: EchoRemoteEndpoint)
    suspend fun rollback(prepared: EchoLinkCastPreparedQueue)
    fun activate(target: EchoRemoteEndpoint)
}

/** Process-owned transaction and display state; never retains an Activity callback or Context. */
internal class EchoPhoneCastTransfer(
    private val host: EchoPhoneCastHost,
    private val scope: CoroutineScope,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutableState = MutableStateFlow(EchoPhoneCastTransferState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0L

    fun cancel() { job?.cancel() }
    fun clearIssue() { mutableState.value = state.value.copy(issue = null) }

    fun start(
        source: () -> EchoPhoneCastSource,
        buildPlan: suspend (EchoPhoneCastSource) -> EchoLinkCastPlan,
        pausePhone: () -> Unit,
    ) {
        if (state.value.isBusy) return
        val target = host.endpoint ?: return
        val snapshot = source()
        val previous = job
        val request = ++generation
        mutableState.value = EchoPhoneCastTransferState(isBusy = true,
            targetAddress = EchoLinkCastPolicy.advertisedBaseUrl(target.host, target.port))
        job = scope.launch {
            var prepared: EchoLinkCastPreparedQueue? = null
            var issue: EchoPhoneCastIssue? = null
            try {
                previous?.join()
                val plan = withContext(workDispatcher) { buildPlan(snapshot) }
                if (plan is EchoLinkCastPlan.Blocked) {
                    issue = if (plan.reason == EchoLinkCastBlockReason.ClippedTrack) EchoPhoneCastIssue.ClippedTrack
                        else EchoPhoneCastIssue.UnsupportedSource
                    return@launch
                }
                checkTarget(target)
                if (plan is EchoLinkCastPlan.HandoffPcLibrary && plan.tracks.size > 1) {
                    issue = EchoPhoneCastIssue.AtomicPcQueueUnavailable
                    return@launch
                }
                if (plan is EchoLinkCastPlan.LocalHttp && plan.tracks.size > 1 && !host.supportsAtomicPhoneQueue) {
                    issue = EchoPhoneCastIssue.AtomicPhoneQueueRequired
                    return@launch
                }
                if (!snapshot.isSameSelection(source())) {
                    issue = EchoPhoneCastIssue.SourceChanged
                    return@launch
                }
                if (plan is EchoLinkCastPlan.LocalHttp) {
                    // Assignment stays inside the dispatched block so prompt cancellation cannot lose the handle.
                    withContext(workDispatcher) { prepared = host.prepare(plan.tracks, target) }
                    if (prepared == null) {
                        issue = EchoPhoneCastIssue.NoLan
                        return@launch
                    }
                }
                checkTarget(target)
                val current = source()
                if (!snapshot.isSameSelection(current)) {
                    issue = EchoPhoneCastIssue.SourceChanged
                    return@launch
                }
                // Read live progress immediately before the one remote mutation, after all preparation.
                host.send(plan, prepared, current.positionMs)
                checkTarget(target)
                host.commit(prepared, target)
                prepared = null
                coroutineContext.ensureActive()
                host.activate(target)
                if (snapshot.isSameSelection(source()) && source().playback.isPlaying) pausePhone()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: EchoLinkAtomicQueueRequiredException) {
                issue = if (error.pcLibrary) EchoPhoneCastIssue.AtomicPcQueueUnavailable else EchoPhoneCastIssue.AtomicPhoneQueueRequired
            } catch (_: Exception) {
                issue = EchoPhoneCastIssue.Failed
            } finally {
                try {
                    prepared?.let { candidate -> withContext(NonCancellable) { host.rollback(candidate) } }
                } finally {
                    if (request == generation) mutableState.value = state.value.copy(isBusy = false, issue = issue)
                }
            }
        }
        job?.invokeOnCompletion {
            if (request == generation && state.value.isBusy) mutableState.value = state.value.copy(isBusy = false)
        }
    }

    private fun checkTarget(target: EchoRemoteEndpoint) {
        check(host.endpoint == target) { "Cast destination changed" }
    }
}
