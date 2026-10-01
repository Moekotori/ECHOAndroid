package app.echo.android.feature.player

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.Album
import app.echo.android.design.EchoIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.echo.android.design.EchoArtworkRequestHeadersRegistry
import app.echo.android.design.EchoArtworkUrlRewriteRegistry
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Uses the app's bounded Coil cache; transforms once per cover, never per progress tick. */
@Composable
internal fun PixelArtwork(uri: String?, description: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val revision = EchoArtworkUrlRewriteRegistry.revision
    val rewritten = EchoArtworkUrlRewriteRegistry.rewrite(uri)
    // A Subsonic URL needs the same signed-URL rewrite used by the normal cover.
    val staleSubsonic = uri != null && rewritten == uri &&
        (uri.contains("/rest/", ignoreCase = true) || uri.contains("/rest?", ignoreCase = true))
    val headers = EchoArtworkRequestHeadersRegistry.headersFor(uri)
    val request = remember(uri, rewritten, revision, headers, staleSubsonic) {
        ImageRequest.Builder(context.applicationContext)
            .data(if (staleSubsonic) null else rewritten)
            .size(256)
            .allowHardware(false)
            .crossfade(false)
            .transformations(PixelArtworkTransformation)
            .apply { headers.forEach { (key, value) -> setHeader(key, value) } }
            .build()
    }
    Box(modifier.background(ExpressivePlayerStyle.PixelPaper), contentAlignment = Alignment.Center) {
        EchoIcon(Icons.Sharp.Album, contentDescription = null, tint = ExpressivePlayerStyle.PixelInk)
        AsyncImage(
            model = request, contentDescription = description,
            contentScale = ContentScale.Crop, filterQuality = FilterQuality.None,
            alignment = BiasAlignment(0f, 0f),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private object PixelArtworkTransformation : Transformation {
    override val cacheKey = "echo-handheld-two-tone-256-v3"
    private val palette = intArrayOf(0xFF163F23.toInt(), 0xFFCCE5AA.toInt())

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = withContext(Dispatchers.Default) {
        val ratio = 256f / maxOf(input.width, input.height)
        val width = (input.width * ratio).toInt().coerceAtLeast(1)
        val height = (input.height * ratio).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(input, width, height, true)
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        if (small !== input) small.recycle()
        var currentError = FloatArray(width + 2)
        var nextError = FloatArray(width + 2)
        for (y in 0 until height) {
            ensureActive()
            for (x in 0 until width) {
                val color = pixels[y * width + x]
                val luma = (((color shr 16) and 255) * 0.299f + ((color shr 8) and 255) * 0.587f + (color and 255) * 0.114f) / 255f
                val contrast = (((luma - 0.12f) / 0.76f) + currentError[x + 1]).coerceIn(0f, 1f)
                val level = contrast.roundToInt().coerceIn(0, 1)
                val error = contrast - level
                currentError[x + 2] += error * 7f / 16f
                nextError[x] += error * 3f / 16f
                nextError[x + 1] += error * 5f / 16f
                nextError[x + 2] += error / 16f
                pixels[y * width + x] = palette[level]
            }
            val consumed = currentError
            currentError = nextError
            nextError = consumed
            nextError.fill(0f)
        }
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
