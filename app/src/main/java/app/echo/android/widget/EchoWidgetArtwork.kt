package app.echo.android.widget

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import app.echo.android.design.EchoArtworkUrlRewriteRegistry
import app.echo.android.playback.EchoPlaybackArtwork
import app.echo.android.playback.EchoPlaybackSurfaceSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** One decoded cover, shared by widget instances. Failures expire so reconnection can recover. */
internal object EchoWidgetArtwork {
    private data class Cached(val key: String, val bitmap: Bitmap?, val expiresAtMs: Long)
    private val mutex = Mutex()
    private var cached: Cached? = null

    suspend fun load(context: Context, snapshot: EchoPlaybackSurfaceSnapshot, allowRemote: Boolean): Bitmap? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val key = "${snapshot.artworkUri.orEmpty()}|${snapshot.playUri.orEmpty()}|$allowRemote"
                cached?.takeIf { it.key == key && SystemClock.elapsedRealtime() < it.expiresAtMs }?.let { return@withLock it.bitmap }
                currentCoroutineContext().ensureActive()
                val local = EchoPlaybackArtwork.load(context, snapshot.artworkUri, snapshot.playUri,
                    EchoPlaybackArtwork.WidgetMaxEdgePx, maxBytes = 2 * 1024 * 1024)
                val bitmap = local ?: if (allowRemote) loadRemote(snapshot.artworkUri) else null
                currentCoroutineContext().ensureActive()
                cached = Cached(key, bitmap, SystemClock.elapsedRealtime() + if (bitmap == null) 30_000 else 300_000)
                bitmap
            }
        }

    private suspend fun loadRemote(artworkUri: String?): Bitmap? {
        val remote = EchoArtworkUrlRewriteRegistry.rewrite(artworkUri)
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return null
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(remote).openConnection() as HttpURLConnection
            connection.connectTimeout = 2_500
            connection.readTimeout = 2_500
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use {
                EchoPlaybackArtwork.decodeCapped(it, EchoPlaybackArtwork.WidgetMaxEdgePx, 2 * 1024 * 1024)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { null }
        finally { connection?.disconnect() }
    }
}
