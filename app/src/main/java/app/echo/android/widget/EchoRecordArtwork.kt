package app.echo.android.widget

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One static record image, bounded to 512px. No animation, UI-thread drawing or per-size copies. */
internal object EchoRecordArtwork {
    private data class Cached(val source: Bitmap, val record: Bitmap)
    private val mutex = Mutex()
    private var cached: Cached? = null

    suspend fun render(source: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        mutex.withLock {
            cached?.takeIf { it.source === source }?.let { return@withLock it.record }
            val edge = 512
            val center = edge / 2f
            val labelRadius = edge * 0.29f
            val image = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(image)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            paint.shader = RadialGradient(edge * 0.32f, edge * 0.24f, edge * 0.8f,
                intArrayOf(Color.rgb(67, 76, 85), Color.rgb(36, 44, 52), Color.rgb(23, 29, 35)),
                null, Shader.TileMode.CLAMP)
            canvas.drawCircle(center, center, center - 1f, paint)
            val scale = labelRadius * 2 / minOf(source.width, source.height)
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(center - source.width * scale / 2, center - source.height * scale / 2)
            }
            paint.shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(matrix)
            }
            canvas.drawCircle(center, center, labelRadius, paint)
            paint.shader = null
            paint.color = Color.rgb(222, 228, 233)
            canvas.drawCircle(center, center, edge * 0.012f, paint)
            cached = Cached(source, image)
            image
        }
    }
}
