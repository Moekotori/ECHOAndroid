package app.echo.android.playback

import android.content.Context
import android.graphics.Bitmap
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
            ?: return null
        return DataSourceBitmapLoader.DEFAULT_EXECUTOR_SERVICE.get().submit(
            Callable {
                EchoPlaybackArtwork.load(
                    context = context,
                    artworkUri = null,
                    embeddedSourceUri = sourceUri,
                    maxEdgePx = EchoPlaybackArtwork.NotificationMaxEdgePx,
                ) ?: throw IOException("No embedded artwork in $sourceUri")
            },
        )
    }
}

internal const val EchoEmbeddedArtworkSourceUriExtra = "app.echo.android.playback.EMBEDDED_ARTWORK_SOURCE_URI"
