package app.echo.android.design

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
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
val EchoGlassNight = Color(0xFF0B1020)
val EchoGlassInk = Color(0xFF141A31)
val EchoGlassPanel = Color(0xFF1D2442)
val EchoGlassViolet = Color(0xFF8B7CFF)
val EchoGlassCyan = Color(0xFF50D6FF)
val EchoGlassRose = Color(0xFFFF7AB8)
val EchoDarkGlassBorder = Color.White.copy(alpha = 0.22f)
val EchoDarkGlassLine = Color.White.copy(alpha = 0.13f)

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    alpha: Float = 0.16f,
    content: @Composable () -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = if (dark) EchoGlassPanel.copy(alpha = (alpha + 0.46f).coerceIn(0.46f, 0.76f)) else Color.White.copy(alpha = alpha),
        border = BorderStroke(
            1.dp,
            if (dark) EchoDarkGlassBorder else Color.White.copy(alpha = 0.32f),
        ),
        content = { content() },
    )
}

@Composable
fun echoDarkGlassBrush(strength: Float = 1f): Brush {
    val dark = LocalEchoDarkTheme.current
    val clamped = strength.coerceIn(0.45f, 1.25f)
    return Brush.linearGradient(
        if (dark) {
            listOf(
                EchoGlassPanel.copy(alpha = 0.68f * clamped),
                EchoGlassInk.copy(alpha = 0.52f * clamped),
                EchoGlassViolet.copy(alpha = 0.20f * clamped),
                EchoGlassCyan.copy(alpha = 0.14f * clamped),
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.96f),
                EchoHomeMist.copy(alpha = 0.90f),
                EchoAccentDeep.copy(alpha = 0.08f),
            )
        },
    )
}

@Composable
fun echoGlassContainerBrush(
    strength: Float = 1f,
    accent: Color = EchoGlassCyan,
): Brush {
    val dark = LocalEchoDarkTheme.current
    val clamped = strength.coerceIn(0.40f, 1.20f)
    return Brush.linearGradient(
        if (dark) {
            listOf(
                EchoGlassPanel.copy(alpha = 0.58f * clamped),
                EchoGlassInk.copy(alpha = 0.40f * clamped),
                accent.copy(alpha = 0.16f * clamped),
                EchoGlassViolet.copy(alpha = 0.12f * clamped),
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.88f),
                EchoHomeMist.copy(alpha = 0.72f),
                accent.copy(alpha = 0.08f),
            )
        },
    )
}

@Composable
fun echoGlassRowBrush(
    selected: Boolean = false,
    accent: Color = EchoGlassCyan,
): Brush {
    val dark = LocalEchoDarkTheme.current
    return Brush.linearGradient(
        if (dark) {
            listOf(
                EchoGlassPanel.copy(alpha = if (selected) 0.58f else 0.44f),
                EchoGlassInk.copy(alpha = if (selected) 0.34f else 0.28f),
                accent.copy(alpha = if (selected) 0.20f else 0.08f),
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.74f),
                EchoHomeMist.copy(alpha = 0.50f),
                accent.copy(alpha = if (selected) 0.12f else 0.04f),
            )
        },
    )
}

@Composable
fun echoDarkGlassBorder(selected: Boolean = false): BorderStroke {
    val dark = LocalEchoDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    return BorderStroke(
        1.dp,
        if (dark) {
            if (selected) scheme.primary.copy(alpha = 0.36f) else EchoDarkGlassBorder
        } else {
            if (selected) scheme.primary.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.84f)
        },
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
                Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
            }
        }
    }
}

@Composable
fun EchoGlassBackground(modifier: Modifier = Modifier) {
    val dark = LocalEchoDarkTheme.current
    val baseGradient = Brush.verticalGradient(
        if (dark) {
            listOf(
                EchoGlassNight,
                Color(0xFF111B34),
                Color(0xFF211A3A),
                Color(0xFF101326),
            )
        } else {
            listOf(
                EchoBgTop,
                EchoBgMid,
                EchoBgBottom,
            )
        },
    )
    Canvas(modifier = modifier.background(baseGradient)) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(EchoGlassCyan.copy(alpha = if (dark) 0.24f else 0.22f), Color.Transparent),
                start = Offset(0f, h * 0.05f),
                end = Offset(w * 0.78f, h * 0.54f),
            ),
        )
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, EchoGlassViolet.copy(alpha = if (dark) 0.30f else 0.20f)),
                start = Offset(w * 0.20f, 0f),
                end = Offset(w, h * 0.66f),
            ),
        )
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, EchoGlassRose.copy(alpha = if (dark) 0.14f else 0.18f), Color.Transparent),
                start = Offset(0f, h * 0.42f),
                end = Offset(w, h),
            ),
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = if (dark) 0.16f else 0.06f)),
                startY = h * 0.50f,
                endY = h,
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
        val densityScale = LocalEchoDensityScale.current
        val dark = LocalEchoDarkTheme.current
        val scheme = MaterialTheme.colorScheme
        val compactChrome = configuration.screenHeightDp < 620 ||
            configuration.screenWidthDp > configuration.screenHeightDp
        val contentScroll = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
        val chromeGradient = if (dark) {
            Brush.verticalGradient(
                listOf(
                    EchoGlassNight.copy(alpha = 0.58f),
                    EchoGlassInk.copy(alpha = 0.42f),
                    EchoGlassViolet.copy(alpha = 0.18f),
                    Color.Transparent,
                ),
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.66f),
                    EchoHomeMist.copy(alpha = 0.78f),
                    Color(0xFFEAF2FF).copy(alpha = 0.86f),
                ),
            )
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(chromeGradient)
                .statusBarsPadding(),
        ) {
            val horizontalPadding = ((if (maxWidth >= 720.dp) 28f else 16f) * densityScale).dp
            val topPadding = when {
                compactHeader -> (2f * densityScale).dp
                compactChrome -> (8f * densityScale).dp
                else -> (14f * densityScale).dp
            }
            val headerGap = when {
                compactHeader -> (4f * densityScale).dp
                compactChrome -> (6f * densityScale).dp
                else -> (8f * densityScale).dp
            }
            val contentGap = when {
                compactHeader -> (4f * densityScale).dp
                compactChrome -> (8f * densityScale).dp
                else -> (12f * densityScale).dp
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
                        Text(title, style = titleStyle, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (dark) EchoGlassPanel.copy(alpha = 0.46f) else scheme.surface.copy(alpha = 0.56f),
                            border = BorderStroke(
                                1.dp,
                                if (dark) EchoDarkGlassBorder else EchoGlassBorder,
                            ),
                        ) {
                            Text(
                                badge,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurface,
                            )
                        }
                        actions()
                    }
                }
                if (showBrand) {
                    Spacer(Modifier.height(headerGap))
                    Text(title, style = titleStyle, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                }
                if (subtitle != null) {
                    Text(subtitle, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    val shape = RoundedCornerShape(cornerRadius)
    EchoArtworkImage(
        artworkUri = artworkUri,
        contentDescription = null,
        modifier = modifier
            .then(
                if (elevation > 0.dp) {
                    Modifier.shadow(elevation = elevation, shape = shape, clip = false)
                } else {
                    Modifier
                },
            )
            .clip(shape),
        shape = shape,
        sizeClass = if (cornerRadius >= 24.dp) EchoArtworkSize.Hero else EchoArtworkSize.Thumbnail,
        accent = accent,
        showSignal = showSignal,
        placeholderIconSize = placeholderIconSize,
    )
}

@Composable
fun EmptyState(message: String) {
    EchoPanel(Modifier.fillMaxWidth()) {
        Text(
            text = message,
            modifier = Modifier.padding(18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
    artworkScale: Float = 1.4f,
    artworkBlur: Dp = 52.dp,
    artworkAlpha: Float = 0.7f,
    overlayStartAlpha: Float = 0.16f,
    overlayMidAlpha: Float = 0.05f,
    overlayEndAlpha: Float = 0.42f,
) {
    val context = LocalContext.current
    val artworkModel = remember(context, artworkUri) {
        ImageRequest.Builder(context)
            .data(artworkUri)
            .size(Size.ORIGINAL)
            .bitmapConfig(Bitmap.Config.ARGB_8888)
            .crossfade(false)
            .build()
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
                            lerp(palette.deep, EchoGlassNight, 0.32f),
                        ),
                    ),
                ),
        )
        if (!artworkUri.isNullOrBlank()) {
            AsyncImage(
                model = artworkModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(artworkScale)
                    .blur(artworkBlur)
                    .alpha(artworkAlpha),
            )
        }
        // 压暗的毛玻璃罩，保证白色文字与控件可读
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            EchoGlassNight.copy(alpha = overlayStartAlpha * 0.70f),
                            EchoGlassInk.copy(alpha = overlayMidAlpha * 0.72f),
                            lerp(palette.deep, EchoGlassPanel, 0.42f).copy(alpha = overlayEndAlpha * 0.86f),
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
