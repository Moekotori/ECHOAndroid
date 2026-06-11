package app.echo.android.playback

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.net.Uri
import android.util.LruCache
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

@UnstableApi
internal class EchoNotificationBitmapLoader(
    private val delegate: BitmapLoader,
) : BitmapLoader {
    private val fallbackArtworkCache = LruCache<String, Bitmap>(FallbackArtworkCacheSize)

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
        val cacheKey = listOfNotNull(
            metadata.title?.toString(),
            metadata.artist?.toString(),
            metadata.albumTitle?.toString(),
        ).joinToString(separator = "|").ifBlank { "echo" }
        val bitmap = fallbackArtworkCache.get(cacheKey) ?: createFallbackArtwork(metadata)
            .also { fallbackArtworkCache.put(cacheKey, it) }
        return Futures.immediateFuture(bitmap)
    }

    private fun createFallbackArtwork(metadata: MediaMetadata): Bitmap {
        val bitmap = Bitmap.createBitmap(FallbackArtworkSize, FallbackArtworkSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val title = metadata.title?.toString().orEmpty().ifBlank { "ECHO" }
        val artist = metadata.artist?.toString().orEmpty()
        val seed = "$title|$artist"
        val accent = EchoAccentPalette[abs(seed.hashCode()) % EchoAccentPalette.size]

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                FallbackArtworkSize.toFloat(),
                FallbackArtworkSize.toFloat(),
                intArrayOf(Color.rgb(8, 11, 18), Color.rgb(20, 24, 34), accent),
                floatArrayOf(0f, 0.62f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, FallbackArtworkSize.toFloat(), FallbackArtworkSize.toFloat(), backgroundPaint)

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(accent, 110)
        }
        canvas.drawCircle(FallbackArtworkSize * 0.78f, FallbackArtworkSize * 0.78f, FallbackArtworkSize * 0.34f, glowPaint)

        val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textSize = 92f
        }
        canvas.drawCenteredText(fallbackMark(title), FallbackArtworkSize / 2f, FallbackArtworkSize * 0.47f, markPaint)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(214, 255, 255, 255)
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textSize = 34f
            letterSpacing = 0.04f
        }
        canvas.drawCenteredText("ECHO", FallbackArtworkSize / 2f, FallbackArtworkSize * 0.68f, labelPaint)

        return bitmap.copy(Bitmap.Config.ARGB_8888, false).also {
            bitmap.recycle()
        }
    }

    private fun Canvas.drawCenteredText(text: String, centerX: Float, baselineCenterY: Float, paint: Paint) {
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        drawText(text, centerX, baselineCenterY - bounds.exactCenterY(), paint)
    }

    private fun fallbackMark(title: String): String {
        val mark = title
            .asSequence()
            .firstOrNull { it.isLetterOrDigit() }
            ?.toString()
            ?.uppercase(Locale.ROOT)
        return mark?.take(2) ?: "E"
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        const val FallbackArtworkSize = 512
        const val FallbackArtworkCacheSize = 16
        val EchoAccentPalette = intArrayOf(
            Color.rgb(45, 128, 255),
            Color.rgb(92, 206, 255),
            Color.rgb(126, 216, 154),
            Color.rgb(247, 197, 107),
            Color.rgb(242, 105, 139),
        )
    }
}
