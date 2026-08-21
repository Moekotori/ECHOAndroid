package app.echo.android.design

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.echo.android.model.settings.EchoEffectivePerformanceMode
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.min

enum class EchoArtworkSize {
    Tiny,
    Thumbnail,
    Card,
    Hero,
}

@Composable
fun EchoArtworkImage(
    artworkUri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    sizeClass: EchoArtworkSize = EchoArtworkSize.Thumbnail,
) {
    EchoArtworkImage(
        artworkUri = artworkUri,
        contentDescription = contentDescription,
        modifier = modifier,
        shape = shape,
        sizeClass = sizeClass,
        accent = EchoAccent,
        showSignal = false,
        placeholderIconSize = null,
    )
}

@Composable
internal fun EchoArtworkImage(
    artworkUri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    sizeClass: EchoArtworkSize = EchoArtworkSize.Thumbnail,
    accent: Color = EchoAccent,
    showSignal: Boolean = false,
    placeholderIconSize: Dp? = null,
) {
    val context = LocalContext.current
    val effectivePerformanceMode = LocalEchoEffectivePerformanceMode.current
    val requestHeaders = EchoArtworkRequestHeadersRegistry.headersFor(artworkUri)
    val rewriteRevision = EchoArtworkUrlRewriteRegistry.revision
    val fetchUri = resolvedArtworkFetchUri(artworkUri)
    val model = remember(
        context,
        artworkUri,
        fetchUri,
        sizeClass,
        requestHeaders,
        effectivePerformanceMode,
        rewriteRevision,
    ) {
        val maxPixelSize = sizeClass.maxPixelSize(effectivePerformanceMode)
        echoArtworkImageRequest(
            context = context,
            originalUri = artworkUri,
            fetchUri = fetchUri,
            maxPixelSize = maxPixelSize,
            headers = requestHeaders,
            highBitDepth = effectivePerformanceMode.isHighPerformance,
        )
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        accent,
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUri.isNullOrBlank()) {
            EchoArtworkPlaceholder(
                sizeClass = sizeClass,
                accent = accent,
                showSignal = showSignal,
                iconSize = placeholderIconSize,
            )
        } else {
            EchoArtworkPlaceholder(
                sizeClass = sizeClass,
                accent = accent,
                showSignal = showSignal,
                iconSize = placeholderIconSize,
            )
            if (fetchUri != null) {
                AsyncImage(
                    model = model,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
private fun EchoArtworkPlaceholder(
    sizeClass: EchoArtworkSize,
    accent: Color,
    showSignal: Boolean,
    iconSize: Dp?,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    val motifSize = iconSize ?: sizeClass.defaultMotifSize(showSignal)
    val baseColors = if (dark) {
        listOf(Color(0xFF20232B), Color(0xFF161A22), Color(0xFF0F1218))
    } else {
        listOf(Color(0xFFEAF0F8), Color(0xFFF8F2F5), Color.White)
    }
    val primaryGlow = if (dark) scheme.primary.copy(alpha = 0.34f) else accent.copy(alpha = 0.30f)
    val secondaryGlow = if (dark) Color(0xFF8FA6B4).copy(alpha = 0.18f) else Color(0xFFB7C8D9).copy(alpha = 0.24f)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val minSide = min(width, height)
            val motifRadius = (motifSize.toPx() * 0.56f).coerceIn(minSide * 0.18f, minSide * 0.36f)
            val recordCenter = Offset(width * 0.38f, height * 0.42f)
            drawRect(
                brush = Brush.linearGradient(
                    colors = baseColors,
                    start = Offset.Zero,
                    end = Offset(width, height),
                ),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryGlow, Color.Transparent),
                    center = Offset(width * 0.22f, height * 0.12f),
                    radius = minSide * 0.92f,
                ),
                radius = minSide * 0.68f,
                center = Offset(width * 0.22f, height * 0.12f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondaryGlow, Color.Transparent),
                    center = Offset(width * 0.82f, height * 0.86f),
                    radius = minSide * 0.74f,
                ),
                radius = minSide * 0.58f,
                center = Offset(width * 0.82f, height * 0.86f),
            )
            drawCircle(
                color = if (dark) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.38f),
                radius = motifRadius * 1.22f,
                center = recordCenter,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (dark) 0.20f else 0.56f),
                        scheme.primary.copy(alpha = if (dark) 0.20f else 0.18f),
                        Color.Transparent,
                    ),
                    center = recordCenter,
                    radius = motifRadius * 1.18f,
                ),
                radius = motifRadius,
                center = recordCenter,
            )
            drawCircle(
                color = Color.White.copy(alpha = if (dark) 0.34f else 0.62f),
                radius = motifRadius,
                center = recordCenter,
                style = Stroke(width = (minSide * 0.035f).coerceAtLeast(1.5f)),
            )
            drawCircle(
                color = if (dark) Color(0xFF121720).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.82f),
                radius = motifRadius * 0.22f,
                center = recordCenter,
            )
            drawLine(
                color = Color.White.copy(alpha = if (dark) 0.18f else 0.46f),
                start = Offset(width * 0.12f, height * 0.72f),
                end = Offset(width * 0.68f, height * 0.72f),
                strokeWidth = (minSide * 0.045f).coerceAtLeast(2f),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = scheme.primary.copy(alpha = if (dark) 0.42f else 0.36f),
                start = Offset(width * 0.12f, height * 0.82f),
                end = Offset(width * 0.48f, height * 0.82f),
                strokeWidth = (minSide * 0.04f).coerceAtLeast(2f),
                cap = StrokeCap.Round,
            )
            drawRoundRect(
                color = Color.White.copy(alpha = if (dark) 0.07f else 0.32f),
                topLeft = Offset(width * 0.72f, height * 0.16f),
                size = Size(width * 0.12f, height * 0.54f),
                cornerRadius = CornerRadius(width * 0.06f, width * 0.06f),
            )
        }
        if (showSignal) {
            EchoArtworkSignalStrip(
                accent = accent,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = sizeClass.signalBottomPadding()),
            )
        }
    }
}

@Composable
private fun EchoArtworkSignalStrip(
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val heights = listOf(12.dp, 24.dp, 16.dp, 34.dp, 22.dp, 42.dp, 28.dp, 18.dp, 30.dp)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEachIndexed { index, height ->
            Surface(
                modifier = Modifier
                    .width(4.dp)
                    .height(height),
                shape = RoundedCornerShape(8.dp),
                color = if (index % 3 == 0) {
                    accent.copy(alpha = 0.92f)
                } else {
                    Color.White.copy(alpha = 0.64f)
                },
                content = {},
            )
        }
    }
}

private fun EchoArtworkSize.defaultMotifSize(showSignal: Boolean): Dp =
    when (this) {
        EchoArtworkSize.Tiny -> 22.dp
        EchoArtworkSize.Thumbnail -> if (showSignal) 38.dp else 32.dp
        EchoArtworkSize.Card -> if (showSignal) 42.dp else 36.dp
        EchoArtworkSize.Hero -> if (showSignal) 48.dp else 42.dp
    }

private fun EchoArtworkSize.signalBottomPadding(): Dp =
    when (this) {
        EchoArtworkSize.Tiny -> 6.dp
        EchoArtworkSize.Thumbnail -> 10.dp
        EchoArtworkSize.Card -> 14.dp
        EchoArtworkSize.Hero -> 18.dp
    }

internal fun echoArtworkImageRequest(
    context: android.content.Context,
    originalUri: String?,
    fetchUri: String?,
    maxPixelSize: Int,
    headers: Map<String, String> = emptyMap(),
    highBitDepth: Boolean,
): ImageRequest {
    val builder = ImageRequest.Builder(context)
        .data(fetchUri ?: originalUri)
        .crossfade(false)
        .size(maxPixelSize, maxPixelSize)
        .bitmapConfig(if (highBitDepth) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
    if (!originalUri.isNullOrBlank()) {
        builder.memoryCacheKey(originalUri)
        builder.diskCacheKey(originalUri)
    }
    headers.forEach { (name, value) ->
        builder.setHeader(name, value)
    }
    return builder.build()
}

private fun EchoArtworkSize.maxPixelSize(effectivePerformanceMode: EchoEffectivePerformanceMode): Int =
    when {
        effectivePerformanceMode.isLightweight -> when (this) {
            EchoArtworkSize.Tiny -> 96
            EchoArtworkSize.Thumbnail -> 160
            EchoArtworkSize.Card -> 256
            EchoArtworkSize.Hero -> 512
        }
        effectivePerformanceMode.isHighPerformance -> when (this) {
            EchoArtworkSize.Tiny -> 128
            EchoArtworkSize.Thumbnail -> 256
            EchoArtworkSize.Card -> 512
            EchoArtworkSize.Hero -> 1024
        }
        else -> when (this) {
            EchoArtworkSize.Tiny -> 112
            EchoArtworkSize.Thumbnail -> 192
            EchoArtworkSize.Card -> 384
            EchoArtworkSize.Hero -> 768
        }
    }
