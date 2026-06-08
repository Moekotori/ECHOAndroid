package app.echo.android.design

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val EchoContentMaxWidth = 560.dp

// === 默认主题：Roon 风格（Shakespeare 蓝 + 中性炭灰）。多主题切换时改这一组即可 ===
val EchoAccent = Color(0xFF4DB7E8)
val EchoAccentText = Color(0xFF1E88C8)
val EchoAccentDeep = Color(0xFF7C6CF2)
val EchoBgTop = Color(0xFFFDFEFF)
val EchoBgMid = Color(0xFFF0F8FF)
val EchoBgBottom = Color(0xFFEAF2FF)
val RoonBlue = Color(0xFF5A6CFF)
val RoonInk = Color(0xFF25242A)
val RoonMuted = Color(0xFF6D6D73)
val RoonPaper = Color(0xFFFDFEFF)
val RoonPanel = Color(0xFFF4F8FF)
val EchoHomeBlue = Color(0xFF4DB7E8)
val EchoHomeBlueDeep = Color(0xFF7C6CF2)
val EchoHomeMist = Color(0xFFF4F8FF)
val EchoGlassBorder = Color(0xCCFFFFFF)
val EchoSoftLine = Color(0xFFD8E8F3)

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    alpha: Float = 0.16f,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = alpha),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f)),
        content = { content() },
    )
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    GlassSurface(modifier = Modifier.size(46.dp)) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            IconButton(onClick = onClick) {
                Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(25.dp))
            }
        }
    }
}

@Composable
fun EchoGlassBackground(modifier: Modifier = Modifier) {
    // Roon 风格：平面中性炭灰，极克制的顶部冷光，无光斑、无星点
    val baseGradient = Brush.verticalGradient(
        listOf(
            EchoBgTop,
            EchoBgMid,
            EchoBgBottom,
        ),
    )
    Canvas(modifier = modifier.background(baseGradient)) {
        val w = size.width
        val h = size.height
        // 顶部一抹极淡的 Roon 蓝冷光，给纯黑一点层次
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(EchoHomeBlue.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(w * 0.08f, h * 0.18f),
                radius = h * 0.40f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(EchoAccentDeep.copy(alpha = 0.18f), Color.Transparent),
                center = Offset(w * 0.92f, h * 0.28f),
                radius = h * 0.46f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFC7E3).copy(alpha = 0.18f), Color.Transparent),
                center = Offset(w * 0.40f, h * 0.78f),
                radius = h * 0.44f,
            ),
        )
    }
}

@Composable
fun AmbientPlanet(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(86.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(78.dp),
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.16f),
            content = {},
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .height(10.dp),
            shape = RoundedCornerShape(8.dp),
            color = EchoAccent.copy(alpha = 0.55f),
            content = {},
        )
    }
}

@Composable
fun PageChrome(
    title: String,
    subtitle: String?,
    badge: String = "移动端",
    scrollable: Boolean = false,
    showBrand: Boolean = false,
    compactHeader: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    key(title) {
        val configuration = LocalConfiguration.current
        val compactChrome = configuration.screenHeightDp < 620 ||
            configuration.screenWidthDp > configuration.screenHeightDp
        val contentScroll = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.66f),
                            EchoHomeMist.copy(alpha = 0.78f),
                            Color(0xFFEAF2FF).copy(alpha = 0.86f),
                        ),
                    ),
                )
                .statusBarsPadding(),
        ) {
            val horizontalPadding = if (maxWidth >= 720.dp) 28.dp else 16.dp
            val topPadding = when {
                compactHeader -> 2.dp
                compactChrome -> 8.dp
                else -> 14.dp
            }
            val headerGap = when {
                compactHeader -> 4.dp
                compactChrome -> 6.dp
                else -> 8.dp
            }
            val contentGap = when {
                compactHeader -> 4.dp
                compactChrome -> 8.dp
                else -> 12.dp
            }
            val titleStyle = when {
                compactHeader -> MaterialTheme.typography.headlineMedium
                compactChrome -> MaterialTheme.typography.headlineSmall
                else -> MaterialTheme.typography.headlineMedium
            }
            Column(
                modifier = Modifier
                    .widthIn(max = EchoContentMaxWidth)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .then(contentScroll)
                    .padding(start = horizontalPadding, end = horizontalPadding, top = topPadding),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showBrand) {
                        Text("ECHO 移动端", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(title, style = titleStyle, fontWeight = FontWeight.Bold, color = RoonInk)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.56f),
                            border = BorderStroke(1.dp, EchoGlassBorder),
                        ) {
                            Text(
                                badge,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = RoonInk,
                            )
                        }
                        actions()
                    }
                }
                if (showBrand) {
                    Spacer(Modifier.height(headerGap))
                    Text(title, style = titleStyle, fontWeight = FontWeight.Bold, color = RoonInk)
                }
                if (subtitle != null) {
                    Text(subtitle, color = RoonMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(contentGap))
                content()
                if (scrollable) {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun ArtworkTile(
    artworkUri: String?,
    modifier: Modifier,
    accent: Color,
    showSignal: Boolean = false,
    cornerRadius: Dp = 14.dp,
    elevation: Dp = 0.dp,
    placeholderIconSize: Dp? = null,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(cornerRadius)
    val bitmap by produceState<Bitmap?>(initialValue = null, artworkUri) {
        value = withContext(Dispatchers.IO) {
            loadArtworkBitmap(context.contentResolver, artworkUri)
        }
    }
    Box(
        modifier = modifier
            .then(
                if (elevation > 0.dp) {
                    Modifier.shadow(elevation = elevation, shape = shape, clip = false)
                } else {
                    Modifier
                },
            )
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
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier.size(placeholderIconSize ?: if (showSignal) 38.dp else 32.dp),
                )
                if (showSignal) {
                    Spacer(Modifier.height(18.dp))
                    EchoSignalStrip()
                }
            }
        }
    }
}

@Composable
private fun EchoSignalStrip() {
    val heights = listOf(12.dp, 24.dp, 16.dp, 34.dp, 22.dp, 42.dp, 28.dp, 18.dp, 30.dp)
    Row(
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
                    EchoAccent.copy(alpha = 0.92f)
                } else {
                    Color.White.copy(alpha = 0.64f)
                },
                content = {},
            )
        }
    }
}

@Composable
fun EmptyState(message: String) {
    EchoPanel(Modifier.fillMaxWidth()) {
        Text(
            text = message,
            modifier = Modifier.padding(18.dp),
            color = RoonMuted,
        )
    }
}

private fun loadArtworkBitmap(contentResolver: android.content.ContentResolver, artworkUri: String?): Bitmap? {
    if (artworkUri.isNullOrBlank()) return null
    return runCatching {
        contentResolver.openInputStream(Uri.parse(artworkUri))?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }.getOrNull()
}

/**
 * 从专辑封面提取的配色，用于沉浸式详情页的渐变背景与强调色。
 */
@Immutable
data class ArtworkPalette(
    val vibrant: Color,
    val deep: Color,
    val soft: Color,
    val onColor: Color,
) {
    companion object {
        val Default = ArtworkPalette(
            vibrant = EchoAccent,
            deep = EchoAccentDeep,
            soft = EchoHomeMist,
            onColor = Color.White,
        )

        /** 没有封面时，用标识串生成一个稳定且好看的配色。 */
        fun fromSeed(seed: String?): ArtworkPalette {
            if (seed.isNullOrBlank()) return Default
            val hue = ((seed.hashCode() % 360) + 360) % 360
            val hsv = floatArrayOf(hue.toFloat(), 0.46f, 0.82f)
            val vibrant = Color(android.graphics.Color.HSVToColor(hsv))
            val deep = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.58f, 0.42f)))
            val soft = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.22f, 0.96f)))
            return ArtworkPalette(vibrant = vibrant, deep = deep, soft = soft, onColor = Color.White)
        }
    }
}

@Composable
fun rememberArtworkPalette(artworkUri: String?, seedKey: String? = artworkUri): ArtworkPalette {
    val context = LocalContext.current
    val palette by produceState(ArtworkPalette.fromSeed(seedKey), artworkUri, seedKey) {
        value = withContext(Dispatchers.IO) {
            val bitmap = loadArtworkSwatch(context.contentResolver, artworkUri)
            if (bitmap != null) {
                extractPalette(bitmap).also { bitmap.recycle() }
            } else {
                ArtworkPalette.fromSeed(seedKey)
            }
        }
    }
    return palette
}

private fun loadArtworkSwatch(
    contentResolver: android.content.ContentResolver,
    artworkUri: String?,
): Bitmap? {
    if (artworkUri.isNullOrBlank()) return null
    return runCatching {
        contentResolver.openInputStream(Uri.parse(artworkUri))?.use { stream ->
            val options = BitmapFactory.Options().apply { inSampleSize = 8 }
            BitmapFactory.decodeStream(stream, null, options)
        }
    }.getOrNull()
}

private fun extractPalette(source: Bitmap): ArtworkPalette {
    val sample = runCatching { Bitmap.createScaledBitmap(source, 32, 32, true) }.getOrNull()
        ?: return ArtworkPalette.Default
    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var count = 0
    var bestScore = -1f
    var bestColor = 0
    val hsv = FloatArray(3)
    for (y in 0 until sample.height) {
        for (x in 0 until sample.width) {
            val pixel = sample.getPixel(x, y)
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)
            rSum += r
            gSum += g
            bSum += b
            count++
            android.graphics.Color.colorToHSV(pixel, hsv)
            val s = hsv[1]
            val v = hsv[2]
            // 偏好鲜艳、亮度适中的像素作为强调色
            if (v in 0.28f..0.96f) {
                val score = s * 1.4f + (1f - kotlin.math.abs(v - 0.62f))
                if (score > bestScore) {
                    bestScore = score
                    bestColor = pixel
                }
            }
        }
    }
    sample.recycle()
    if (count == 0) return ArtworkPalette.Default

    val avgColor = android.graphics.Color.rgb((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
    val accentSource = if (bestScore > 0f) bestColor else avgColor

    android.graphics.Color.colorToHSV(accentSource, hsv)
    val baseHue = hsv[0]
    val baseSat = hsv[1].coerceIn(0.2f, 0.85f)
    val vibrant = Color(android.graphics.Color.HSVToColor(floatArrayOf(baseHue, (baseSat + 0.06f).coerceAtMost(0.9f), 0.78f)))
    val deep = Color(android.graphics.Color.HSVToColor(floatArrayOf(baseHue, (baseSat + 0.12f).coerceAtMost(0.95f), 0.40f)))
    val soft = Color(android.graphics.Color.HSVToColor(floatArrayOf(baseHue, (baseSat * 0.4f).coerceIn(0.06f, 0.30f), 0.96f)))
    val onColor = if (hsv[2] > 0.7f && baseSat < 0.45f) Color(0xFF1C1C20) else Color.White
    return ArtworkPalette(vibrant = vibrant, deep = deep, soft = soft, onColor = onColor)
}

/**
 * Apple Music 风格：把当前封面放大模糊成毛玻璃背景，随歌变色；无封面或低版本回退到取色渐变。
 */
@Composable
fun BlurredArtworkBackground(
    artworkUri: String?,
    palette: ArtworkPalette,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, artworkUri) {
        value = withContext(Dispatchers.IO) {
            loadArtworkBitmap(context.contentResolver, artworkUri)
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        // 取色底，保证无封面/低于 API 31 时也有沉浸色
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            palette.vibrant,
                            palette.deep,
                            lerp(palette.deep, Color.Black, 0.40f),
                        ),
                    ),
                ),
        )
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.4f)
                    .blur(52.dp)
                    .alpha(0.7f),
            )
        }
        // 压暗的毛玻璃罩，保证白色文字与控件可读
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.16f),
                            Color.Black.copy(alpha = 0.05f),
                            Color.Black.copy(alpha = 0.42f),
                        ),
                    ),
                ),
        )
    }
}

fun progressFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
