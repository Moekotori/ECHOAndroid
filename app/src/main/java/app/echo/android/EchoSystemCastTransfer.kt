@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package app.echo.android

import android.app.Application
import android.content.ComponentName
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.echo.android.connect.*
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.model.connect.EchoLanRenderer
import app.echo.android.model.connect.EchoRemoteStreamItem
import app.echo.android.playback.EchoPlaybackService
import app.echo.android.playback.queueContext
import app.echo.android.playback.toEchoTrackRef
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal interface EchoExternalCastController {
    val active: Boolean
    fun playPause()
    fun skip(delta: Int)
    fun seek(positionMs: Long)
    fun adjustVolume(delta: Float)
    fun stop()
}

/** Owns one controller and the existing 50-item LAN publication window for one system Cast transfer. */
internal class EchoSystemCastTransfer(private val app: Application, private val link: EchoLinkSession) : EchoExternalCastController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sdk = EchoSystemCastClient(app)
    val available = sdk.available
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    override var active = false
        private set
    private var controller: MediaController? = null
    private var job: Job? = null
    private var generation = 0L
    private var sources: List<EchoLinkCastSourceTrack> = emptyList()
    private var items: List<EchoRemoteStreamItem> = emptyList()
    private var byUrl: Map<String, Int> = emptyMap()
    private var nativeIndices: List<Int> = emptyList()
    private var nativeEntries: List<String?> = emptyList()
    private var nativeCount = 0
    private var anchor: String? = null
    private var renderer: EchoLanRenderer? = null
    private var last: EchoSystemCastMediaState? = null
    private var returning = false
    private val nativeListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            // A new local playback intent takes ownership; never seek its queue back to the old cast.
            if (active && !returning && player.playWhenReady) link.stopLocalCast()
        }
    }

    fun start() {
        sdk.onReady = ::prepare
        sdk.onState = ::publish
        sdk.onEnded = { state -> if (active) returnToPhone(state ?: last, resume = false) }
        sdk.onReturnLocal = { state -> if (active) returnToPhone(state ?: last, resume = state?.playing ?: last?.playing ?: false) }
        sdk.onError = { if (active) returnToPhone(last, resume = false); fail() }
        sdk.initialize()
    }

    fun showOutputSwitcher() {
        if (!sdk.showOutputSwitcher()) fail(stopSession = false)
    }

    private fun prepare(target: EchoLanRenderer, resumed: Boolean) {
        if (resumed) {
            if (active) { renderer = target; link.updateSystemCastPeer(target.host) }
            else { sdk.end(false); fail(stopSession = false) }
            return
        }
        active = false
        job?.cancel()
        controller?.removeListener(nativeListener)
        val epoch = ++generation
        job = scope.launch {
            try {
                if (!app.hasEchoLocalNetworkAccess()) throw IllegalStateException("local-network-unavailable")
                link.clearForSystemCast()
                active = true
                val local = withTimeout(8_000) { connect() }
                controller?.release(); controller = local
                val startIndex = local.currentMediaItemIndex.coerceAtLeast(0)
                nativeCount = local.mediaItemCount
                anchor = local.currentMediaItem?.queueContext()?.entryId ?: local.currentMediaItem?.mediaId
                require(nativeCount > 0 && startIndex < nativeCount)
                val indices = ArrayList<Int>()
                var cursor = startIndex
                while (cursor >= 0 && cursor !in indices && indices.size < EchoLinkCastPolicy.MaxQueueSize) {
                    indices += cursor
                    cursor = local.currentTimeline.getNextWindowIndex(cursor,
                        if (local.repeatMode == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF,
                        local.shuffleModeEnabled)
                }
                val queue = indices.map { local.getMediaItemAt(it).toEchoTrackRef() }
                val entryKeys = indices.map { index -> local.getMediaItemAt(index).let { it.queueContext()?.entryId ?: it.mediaId } }
                if (queue.any { it.clipStartMs > 0 || it.clipEndMs > 0 }) throw IllegalStateException("unsupported-format")
                val uriMimes = withContext(Dispatchers.IO) {
                    EchoLibraryDatabase.create(app).trackDao().getTracksByIds(queue.map { it.id }).associate { it.id to it.mimeType }
                }
                val plan = EchoLinkCastPolicy.plan(queue.map {
                    EchoLinkCastSourceTrack(it.id, it.uri, it.title, it.artist, it.album, it.artworkUri, it.durationMs,
                        mimeType = uriMimes[it.id], sourceId = it.sourceId, sampleRateHz = it.sampleRateHz)
                }, 0) as? EchoLinkCastPlan.LocalHttp ?: throw IllegalStateException("unsupported-source")
                val startPosition = local.currentPosition.coerceAtLeast(0)
                val autoplay = local.playWhenReady
                val published = withContext(Dispatchers.IO) {
                    val address = EchoLinkLanAddresses.ipv4ForPeer(target.host, target.port)
                        ?: throw IllegalStateException("no-route")
                    link.publishLocalCast(plan.tracks, target.host, address)
                } ?: throw IllegalStateException("no-stream")
                if (published.any { !EchoChromecastCastPolicy.canPlay(it) }) throw IllegalStateException("unsupported-format")
                if (epoch != generation) return@launch
                nativeIndices = indices.take(plan.tracks.size)
                nativeEntries = entryKeys.take(plan.tracks.size)
                sources = plan.tracks; items = published; byUrl = published.mapIndexed { index, item -> item.streamUrl to index }.toMap()
                renderer = target
                sdk.load(published, plan.startIndex, startPosition, autoplay, local.repeatMode) { accepted ->
                    if (epoch != generation) return@load
                    val sameQueue = EchoSystemCastReturnPolicy.sameQueue(nativeCount, local.mediaItemCount, anchor,
                        local.currentMediaItem?.queueContext()?.entryId ?: local.currentMediaItem?.mediaId)
                    if (!accepted || !sameQueue) { fail(); return@load }
                    local.pause()
                    active = true
                    local.addListener(nativeListener)
                    link.markCastStarted(target.name)
                    _error.value = null
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { if (epoch == generation) fail(messageId = when (error.message) {
                "unsupported-format", "unsupported-source" -> R.string.system_cast_unsupported_format
                "local-network-unavailable" -> R.string.system_cast_permission
                else -> R.string.system_cast_failed
            }) }
        }
    }

    private fun publish(state: EchoSystemCastMediaState) {
        val index = EchoSystemCastReturnPolicy.index(byUrl, state.contentId) ?: return
        last = state
        if (!active) return
        if (state.idleError) { returnToPhone(state, resume = false); sdk.end(false); fail(stopSession = false); return }
        val source = sources.getOrNull(index) ?: return
        link.publishSystemCastPlayback(renderer?.name.orEmpty(), index, source, state)
    }

    private fun returnToPhone(state: EchoSystemCastMediaState?, resume: Boolean) {
        val index = EchoSystemCastReturnPolicy.index(byUrl, state?.contentId)
        val local = controller
        val validQueue = local != null && EchoSystemCastReturnPolicy.sameQueue(nativeCount, local.mediaItemCount, anchor,
            local.currentMediaItem?.queueContext()?.entryId ?: local.currentMediaItem?.mediaId)
        val nativeTarget = if (validQueue && index != null) EchoSystemCastReturnPolicy.targetIndex(nativeEntries.getOrNull(index),
            nativeIndices.filter { it < local!!.mediaItemCount }.map { nativeIndex ->
                nativeIndex to local!!.getMediaItemAt(nativeIndex).let { it.queueContext()?.entryId ?: it.mediaId }
            }) else null
        returning = true; active = false
        ++generation; job?.cancel(); job = null
        if (validQueue && nativeTarget != null && state != null) {
            local!!.seekTo(nativeTarget, EchoSystemCastReturnPolicy.position(state.positionMs, state.durationMs))
            if (resume) { local.prepare(); local.play() } else local.pause()
        }
        link.stopLocalCast()
        // Commands are delivered on the session looper before releasing this bounded controller.
        val retained = controller
        controller = null
        scope.launch { delay(250); retained?.removeListener(nativeListener); retained?.release() }
        sources = emptyList(); items = emptyList(); byUrl = emptyMap(); nativeIndices = emptyList(); nativeEntries = emptyList(); last = null; renderer = null; returning = false
    }

    private fun fail(stopSession: Boolean = true, messageId: Int = R.string.system_cast_failed) {
        _error.value = app.getString(messageId)
        Toast.makeText(app, _error.value, Toast.LENGTH_LONG).show()
        if (stopSession) { stop(); link.stopLocalCast() }
    }

    override fun playPause() = sdk.playPause()
    override fun skip(delta: Int) = sdk.skip(delta)
    override fun seek(positionMs: Long) = sdk.seek(positionMs)
    override fun adjustVolume(delta: Float) = sdk.adjustVolume(delta)
    override fun stop() {
        active = false; ++generation; job?.cancel(); job = null
        controller?.removeListener(nativeListener); controller?.release(); controller = null
        sources = emptyList(); items = emptyList(); byUrl = emptyMap(); nativeIndices = emptyList(); nativeEntries = emptyList(); last = null; renderer = null
        sdk.end()
    }

    private suspend fun connect(): MediaController = suspendCancellableCoroutine { continuation ->
        val executor = ContextCompat.getMainExecutor(app)
        val future = MediaController.Builder(app, SessionToken(app, ComponentName(app, EchoPlaybackService::class.java))).buildAsync()
        continuation.invokeOnCancellation { executor.execute { MediaController.releaseFuture(future) } }
        future.addListener({ try {
            continuation.resume(future.get()) { _, connected, _ -> executor.execute { connected.release() } }
        } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) } }, executor)
    }

    fun release() { stop(); sdk.release(); scope.cancel() }
}
