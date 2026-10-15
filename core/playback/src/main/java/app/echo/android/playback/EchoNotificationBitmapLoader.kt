package app.echo.android.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.IOException
import java.util.concurrent.Callable

@UnstableApi
internal class EchoNotificationBitmapLoader(
    private val context: Context,
    private val delegate: BitmapLoader,
) : BitmapLoader {
    // 通知在播放状态变化时反复刷新,而嵌入封面要用 MediaMetadataRetriever 解析整个音频文件;
    // 按源 URI 缓存解码结果(含"无封面"的否定结果),同曲目刷新不再重复解析。
    private val bitmapCache = LruCache<String, Bitmap>(BitmapCacheEntries)
    private val noArtworkCache = LruCache<String, Boolean>(NoArtworkCacheEntries)

    override fun supportsMimeType(mimeType: String): Boolean =
        delegate.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        delegate.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        val key = uri.toString()
        bitmapCache.get(key)?.let { return Futures.immediateFuture(it) }
        val image = EchoNotificationArtwork.loader?.invoke(uri) ?: return delegate.loadBitmap(uri)
        return Futures.transform(image, { bitmap -> requireNotNull(bitmap).also { bitmapCache.put(key, it) } },
            MoreExecutors.directExecutor())
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        if (metadata.artworkData != null) {
            return delegate.loadBitmapFromMetadata(metadata)
        }
        metadata.artworkUri?.let { return loadBitmap(it) }
        val sourceUri = metadata.extras
            ?.getString(EchoEmbeddedArtworkSourceUriExtra)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        bitmapCache.get(sourceUri)?.let { return Futures.immediateFuture(it) }
        if (noArtworkCache.get(sourceUri) != null) return null
        return DataSourceBitmapLoader.DEFAULT_EXECUTOR_SERVICE.get().submit(
            Callable {
                val bitmap = EchoPlaybackArtwork.load(
                    context = context,
                    artworkUri = null,
                    embeddedSourceUri = sourceUri,
                    maxEdgePx = EchoPlaybackArtwork.NotificationMaxEdgePx,
                )
                if (bitmap == null) {
                    noArtworkCache.put(sourceUri, true)
                    throw IOException("No embedded artwork in $sourceUri")
                }
                bitmapCache.put(sourceUri, bitmap)
                bitmap
            },
        )
    }

    private companion object {
        // 512px RGB_565 约 0.5MB/张,4 张约 2MB
        const val BitmapCacheEntries = 4
        const val NoArtworkCacheEntries = 32
    }
}

internal const val EchoEmbeddedArtworkSourceUriExtra = "app.echo.android.playback.EMBEDDED_ARTWORK_SOURCE_URI"
