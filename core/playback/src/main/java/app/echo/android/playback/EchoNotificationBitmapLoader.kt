package app.echo.android.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import java.io.IOException
import java.util.concurrent.Callable

@UnstableApi
internal class EchoNotificationBitmapLoader(
    private val context: Context,
    private val delegate: BitmapLoader,
) : BitmapLoader {
    override fun supportsMimeType(mimeType: String): Boolean =
        delegate.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        delegate.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        delegate.loadBitmap(uri)

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        if (metadata.artworkData != null || metadata.artworkUri != null) {
            return delegate.loadBitmapFromMetadata(metadata)
        }
        val sourceUri = metadata.extras
            ?.getString(EchoEmbeddedArtworkSourceUriExtra)
            ?.takeIf { it.isNotBlank() }
            ?.let(Uri::parse)
            ?.takeIf { it.scheme == "content" || it.scheme == "file" }
            ?: return null
        return DataSourceBitmapLoader.DEFAULT_EXECUTOR_SERVICE.get().submit(
            Callable {
                readEmbeddedArtwork(sourceUri)
                    ?: throw IOException("No embedded artwork in $sourceUri")
            },
        )
    }

    private fun readEmbeddedArtwork(sourceUri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, sourceUri)
            val data = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            val longest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
            var sampleSize = 1
            while (longest / sampleSize > NotificationArtworkMaxEdgePx) {
                sampleSize *= 2
            }
            val decode = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeByteArray(data, 0, data.size, decode)
        } catch (_: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }
}

internal const val EchoEmbeddedArtworkSourceUriExtra = "app.echo.android.playback.EMBEDDED_ARTWORK_SOURCE_URI"
private const val NotificationArtworkMaxEdgePx = 512
