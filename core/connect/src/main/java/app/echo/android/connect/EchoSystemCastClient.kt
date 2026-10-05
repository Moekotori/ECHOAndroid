package app.echo.android.connect

import android.content.Context
import android.os.Build
import android.net.Uri
import androidx.mediarouter.app.SystemOutputSwitcherDialogController
import app.echo.android.model.connect.EchoLanRenderer
import app.echo.android.model.connect.EchoLanRendererKind
import app.echo.android.model.connect.EchoRemoteStreamItem
import com.google.android.gms.cast.*
import com.google.android.gms.cast.framework.*
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

data class EchoSystemCastMediaState(val contentId: String?, val positionMs: Long, val durationMs: Long,
    val playing: Boolean, val volume: Float?, val idleError: Boolean = false)

/** Main-thread SDK adapter. No SDK types cross the app-facing boundary. */
class EchoSystemCastClient(context: Context) {
    private val context = context.applicationContext
    private var castContext: CastContext? = null
    private var client: RemoteMediaClient? = null
    private var initialized = false
    private var localTransferPending = false
    private var last: EchoSystemCastMediaState? = null
    private val _available = MutableStateFlow(false)
    val available = _available.asStateFlow()
    var onReady: ((EchoLanRenderer, Boolean) -> Unit)? = null
    var onState: ((EchoSystemCastMediaState) -> Unit)? = null
    var onEnded: ((EchoSystemCastMediaState?) -> Unit)? = null
    var onReturnLocal: ((EchoSystemCastMediaState?) -> Unit)? = null
    var onError: (() -> Unit)? = null

    private val mediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = emit()
        override fun onMetadataUpdated() = emit()
    }
    private val progress = RemoteMediaClient.ProgressListener { _, _ -> emit() }
    private val sessions = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) { }
        override fun onSessionStarted(session: CastSession, sessionId: String) = attach(session, false)
        override fun onSessionStartFailed(session: CastSession, error: Int) { onError?.invoke() }
        override fun onSessionEnding(session: CastSession) { emit() }
        override fun onSessionEnded(session: CastSession, error: Int) {
            detach()
            if (!localTransferPending) onEnded?.invoke(last)
        }
        override fun onSessionResuming(session: CastSession, sessionId: String) { }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = attach(session, true)
        override fun onSessionResumeFailed(session: CastSession, error: Int) { detach(); onEnded?.invoke(last) }
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            emit(); last?.copy(playing = false)?.let { onState?.invoke(it) }; detach()
        }
    }
    private val transfers = object : SessionTransferCallback() {
        override fun onTransferring(transferType: Int) {
            if (transferType == TRANSFER_TYPE_FROM_REMOTE_TO_LOCAL) { emit(); localTransferPending = true }
        }
        override fun onTransferred(transferType: Int, sessionState: SessionState) {
            if (transferType != TRANSFER_TYPE_FROM_REMOTE_TO_LOCAL) return
            localTransferPending = false
            val load = sessionState.loadRequestData
            val media = load?.mediaInfo ?: load?.queueData?.let { queue -> queue.items?.getOrNull(queue.startIndex)?.media }
            val state = last?.let { previous -> previous.copy(contentId = media?.contentId ?: previous.contentId,
                positionMs = load?.currentTime?.takeIf { it >= 0 } ?: previous.positionMs,
                playing = load?.autoplay ?: previous.playing) }
            onReturnLocal?.invoke(state)
            last = null
        }
        override fun onTransferFailed(transferType: Int, reason: Int) {
            if (transferType == TRANSFER_TYPE_FROM_REMOTE_TO_LOCAL) { localTransferPending = false; onError?.invoke() }
        }
    }

    fun initialize() {
        if (initialized) return
        initialized = true
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) return
        val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "echo-cast-init").apply { isDaemon = true } }
        try {
            CastContext.getSharedInstance(context, executor).addOnCompleteListener { task ->
                executor.shutdown()
                if (!task.isSuccessful) return@addOnCompleteListener
                val sdk = task.result
                castContext = sdk
                sdk.sessionManager.addSessionManagerListener(sessions, CastSession::class.java)
                sdk.addSessionTransferCallback(transfers)
                _available.value = Build.VERSION.SDK_INT >= 33
                sdk.sessionManager.currentCastSession?.takeIf { it.isConnected }?.let { attach(it, true) }
            }
        } catch (_: Exception) { executor.shutdown(); _available.value = false }
    }

    fun showOutputSwitcher(): Boolean = _available.value && runCatching {
        SystemOutputSwitcherDialogController.showDialog(context)
    }.getOrDefault(false)

    private fun attach(session: CastSession, resumed: Boolean) {
        detach()
        localTransferPending = false
        client = session.remoteMediaClient
        client?.registerCallback(mediaCallback)
        client?.addProgressListener(progress, 1000)
        val device = session.castDevice ?: return
        val host = device.inetAddress?.hostAddress ?: return
        onReady?.invoke(EchoLanRenderer("system-cast:${device.deviceId}", device.friendlyName ?: "Cast", host,
            EchoChromecastCastPolicy.DefaultPort, EchoLanRendererKind.Chromecast), resumed)
        emit()
    }

    private fun detach() {
        client?.unregisterCallback(mediaCallback); client?.removeProgressListener(progress); client = null
    }

    private fun emit() {
        val remote = client ?: return
        val status = remote.mediaStatus ?: return
        val state = EchoSystemCastMediaState(remote.mediaInfo?.contentId, remote.approximateStreamPosition.coerceAtLeast(0),
            remote.mediaInfo?.streamDuration?.coerceAtLeast(0) ?: 0L,
            status.playerState == MediaStatus.PLAYER_STATE_PLAYING || status.playerState == MediaStatus.PLAYER_STATE_BUFFERING,
            castContext?.sessionManager?.currentCastSession?.volume?.toFloat(), status.idleReason == MediaStatus.IDLE_REASON_ERROR)
        last = state; onState?.invoke(state)
    }

    fun load(items: List<EchoRemoteStreamItem>, index: Int, positionMs: Long, autoplay: Boolean, repeatMode: Int, onResult: (Boolean) -> Unit) {
        val remote = client ?: return onResult(false)
        require(items.isNotEmpty() && items.size <= EchoLinkCastPolicy.MaxQueueSize)
        val queue = items.mapIndexed { queueIndex, item ->
            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                putString(MediaMetadata.KEY_TITLE, item.title); putString(MediaMetadata.KEY_ARTIST, item.artist)
                item.album?.let { putString(MediaMetadata.KEY_ALBUM_TITLE, it) }
                item.artworkUrl?.let { addImage(WebImage(Uri.parse(it))) }
            }
            MediaQueueItem.Builder(MediaInfo.Builder(item.streamUrl).setContentType(EchoDlnaCastPolicy.offeredMime(item))
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setStreamDuration(item.durationMs.coerceAtLeast(0))
                .setMetadata(metadata).build()).setAutoplay(autoplay || queueIndex != index).build()
        }.toTypedArray()
        remote.queueLoad(queue, index.coerceIn(0, queue.lastIndex), when (repeatMode) { 1 -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE; 2 -> MediaStatus.REPEAT_MODE_REPEAT_ALL; else -> MediaStatus.REPEAT_MODE_REPEAT_OFF },
            positionMs.coerceAtLeast(0), null).setResultCallback({ result ->
                onResult(result.status.isSuccess); emit()
            }, 10, java.util.concurrent.TimeUnit.SECONDS)
    }

    fun playPause() { client?.let { if (it.isPlaying || it.isBuffering) it.pause() else it.play() } }
    fun skip(delta: Int) { client?.let { if (delta > 0) it.queueNext(null) else it.queuePrev(null) } }
    fun seek(positionMs: Long) { client?.seek(MediaSeekOptions.Builder().setPosition(positionMs.coerceAtLeast(0)).build()) }
    fun adjustVolume(delta: Float) { castContext?.sessionManager?.currentCastSession?.let { it.volume = (it.volume + delta).coerceIn(0.0, 1.0) } }
    fun end(stopCasting: Boolean = true) { localTransferPending = false; castContext?.sessionManager?.endCurrentSession(stopCasting) }
    fun release() {
        detach(); castContext?.sessionManager?.removeSessionManagerListener(sessions, CastSession::class.java)
        castContext?.removeSessionTransferCallback(transfers); castContext = null; _available.value = false
    }
}
