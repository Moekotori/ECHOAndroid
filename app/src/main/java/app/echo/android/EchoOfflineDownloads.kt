package app.echo.android

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.media3.common.util.UnstableApi
import app.echo.android.data.EchoSettingsStore
import app.echo.android.data.LibraryOfflineDownloadRequest
import app.echo.android.data.LibraryOfflineDownloader
import app.echo.android.data.LibraryOfflineFileEntity
import app.echo.android.data.LibraryOfflineStore
import app.echo.android.model.library.LibraryOfflinePin
import app.echo.android.model.library.LibraryOfflinePolicy
import app.echo.android.model.playback.EchoLinkPlaybackUri
import app.echo.android.playback.EchoOfflinePlaybackIndex
import app.echo.android.playback.EchoPlaybackProcessRuntime
import app.echo.android.playback.EchoRemotePlaybackAuthRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

@UnstableApi
internal class EchoOfflineDownloads(
    private val app: Application,
    private val store: LibraryOfflineStore,
    private val settings: EchoSettingsStore,
) {
    private val downloader = LibraryOfflineDownloader()
    private val wake = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val semaphore = Semaphore(LibraryOfflinePolicy.MaxConcurrentDownloads)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        EchoPlaybackProcessRuntime.scope.launch {
            refreshIndex()
            wake.tryEmit(Unit)
            wake.collect { pump() }
        }
        EchoPlaybackProcessRuntime.scope.launch {
            settings.appSettings.collect { wake.tryEmit(Unit) }
        }
        registerNetworkCallback()
    }

    suspend fun pinAlbum(albumKey: String, title: String): LibraryOfflinePin? {
        val pin = store.pinAlbum(albumKey, title)
        wake.tryEmit(Unit)
        return pin
    }

    suspend fun pinPlaylist(playlistId: String, title: String): LibraryOfflinePin? {
        val pin = store.pinPlaylist(playlistId, title)
        wake.tryEmit(Unit)
        return pin
    }

    suspend fun unpin(pinId: String) {
        store.unpin(pinId)
        refreshIndex()
    }

    fun observePin(pinId: String) = store.observePin(pinId)

    fun observePins() = store.observePins()

    fun observeUsedBytes(): Flow<Long> = store.observeUsedBytes()

    suspend fun usedBytes(): Long = store.usedBytes()

    private suspend fun pump() {
        if (!canDownloadNow()) return
        val queued = store.queuedFiles(LibraryOfflinePolicy.MaxConcurrentDownloads * 4)
        val jobs = ArrayList<Job>(queued.size)
        for (file in queued) {
            if (!canDownloadNow()) break
            if (!inFlight.add(file.trackId)) continue
            jobs += EchoPlaybackProcessRuntime.scope.launch {
                try {
                    semaphore.withPermit { downloadOne(file) }
                } finally {
                    inFlight.remove(file.trackId)
                }
            }
        }
        jobs.forEach { it.join() }
        if (store.queuedFiles(1).isNotEmpty() && canDownloadNow()) {
            delay(250)
            wake.tryEmit(Unit)
        }
    }

    private suspend fun downloadOne(file: LibraryOfflineFileEntity) {
        try {
            if (!canDownloadNow()) return
            if (!store.isTracked(file.trackId)) return
            store.markDownloading(file.trackId)
            val remaining = LibraryOfflinePolicy.remainingQuota(store.usedBytes())
            val dest = store.destinationFile(file.trackId)
            val playUri = EchoPlaybackProcessRuntime.resolveRemoteStreamUri(file.trackId, file.remoteUri)
            if (EchoLinkPlaybackUri.trackIdFromPersistUri(playUri) != null) {
                store.markFailed(file.trackId, "not_connected")
                return
            }
            val request = EchoRemotePlaybackAuthRegistry.playbackRequest(playUri)
            if (!request.url.startsWith("http://") && !request.url.startsWith("https://")) {
                store.markFailed(file.trackId, "unsupported")
                return
            }
            downloader.download(
                request = LibraryOfflineDownloadRequest(request.url, request.headers),
                destination = dest,
                remainingQuotaBytes = remaining,
                usableSpaceBytes = dest.usableSpace,
            )
            if (!store.isTracked(file.trackId)) {
                dest.delete()
                return
            }
            store.markReady(file.trackId, dest)
            refreshIndex()
        } catch (cancelled: CancellationException) {
            store.markFailed(file.trackId, "cancelled")
            throw cancelled
        } catch (error: Throwable) {
            store.markFailed(file.trackId, error.message ?: "download")
        }
    }

    private suspend fun canDownloadNow(): Boolean {
        val wifiOnly = settings.appSettings.first().offlineWifiOnly
        return !wifiOnly || onUnmeteredNetwork(app)
    }

    private suspend fun refreshIndex() {
        EchoOfflinePlaybackIndex.replace(store.readyPathMap())
    }

    private fun registerNetworkCallback() {
        val manager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        runCatching {
            manager.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        wake.tryEmit(Unit)
                    }
                },
            )
        }
    }
}

private fun onUnmeteredNetwork(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}
