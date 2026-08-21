package app.echo.android.design

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val EchoContentMaxWidth = 560.dp

// === 榛樿涓婚锛氬共鍑€钃濋潚 + 涓€х偔鐏般€傚涓婚鍒囨崲鏃舵敼杩欎竴缁勫嵆鍙?===
val EchoAccent = Color(0xFF2F9CFF)
val EchoAccentText = Color(0xFF5BB8FF)
val EchoAccentDeep = Color(0xFF176BBD)
val EchoBgTop = Color(0xFFF8FBFF)
val EchoBgMid = Color(0xFFF1F5FB)
val EchoBgBottom = Color(0xFFEAF0F8)
val RoonInk = Color(0xFF25242A)
val RoonMuted = Color(0xFF6D6D73)
val EchoHomeBlue = Color(0xFF2F9CFF)
val EchoHomeBlueDeep = Color(0xFF176BBD)
val EchoHomeMist = Color(0xFFEFF4FA)
val EchoGlassBorder = Color(0xFFE1E8F2)
val EchoSoftLine = Color(0xFFD5E0EC)
val EchoGlassNight = Color(0xFF17171B)
val EchoGlassInk = Color(0xFF202126)
val EchoGlassPanel = Color(0xFF2A2B30)
val EchoGlassViolet = Color(0xFF252329)
val EchoGlassCyan = Color(0xFF2D2E33)
val EchoDarkGlassBorder = Color.White.copy(alpha = 0.08f)

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    alpha: Float = 0.16f,
    content: @Composable () -> Unit,
) {
    val dark = LocalEchoDarkTheme.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = if (dark) EchoGlassPanel.copy(alpha = (alpha + 0.40f).coerceIn(0.48f, 0.68f)) else Color.White.copy(alpha = alpha),
        border = BorderStroke(
            1.dp,
            if (dark) EchoDarkGlassBorder else Color.White.copy(alpha = 0.32f),
        ),
        content = { content() },
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
                Color.White.copy(alpha = 0.030f * clamped),
                EchoGlassPanel.copy(alpha = 0.50f * clamped),
                EchoGlassInk.copy(alpha = 0.64f * clamped),
                accent.copy(alpha = 0.05f * clamped),
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.98f),
                Color(0xFFF6F9FD),
                EchoHomeMist.copy(alpha = 0.92f),
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
                Color.White.copy(alpha = if (selected) 0.06f else 0.025f),
                EchoGlassPanel.copy(alpha = if (selected) 0.56f else 0.44f),
                EchoGlassInk.copy(alpha = if (selected) 0.60f else 0.50f),
                accent.copy(alpha = if (selected) 0.08f else 0.04f),
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.98f),
                EchoHomeMist.copy(alpha = 0.82f),
                if (selected) accent.copy(alpha = 0.08f) else Color(0xFFF6F8FC),
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
            if (selected) scheme.primary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f)
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
                Color(0xFF1D1D21),
                EchoGlassInk,
                Color(0xFF151519),
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
        val h = size.height
        if (!dark) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.28f), Color.Transparent),
                    startY = 0f,
                    endY = h * 0.45f,
                ),
            )
        }
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = if (dark) 0.08f else 0.04f)),
                startY = h * 0.44f,
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
    badge: String? = null,
    scrollable: Boolean = false,
    scrollBottomPadding: Dp = 12.dp,
    showBrand: Boolean = false,
    compactHeader: Boolean = false,
    badgeContent: (@Composable () -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    key(title) {
        val windowInfo = LocalWindowInfo.current
        val density = LocalDensity.current
        val densityScale = LocalEchoDensityScale.current
        val dark = LocalEchoDarkTheme.current
        val scheme = MaterialTheme.colorScheme
        val heightDp = (windowInfo.containerSize.height / density.density).dp
        val widthDp = (windowInfo.containerSize.width / density.density).dp
        val compactChrome = heightDp < 620.dp || widthDp > heightDp
        val resolvedBadge = badge ?: stringResource(R.string.chrome_badge_mobile)
        val contentScroll = if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
        val chromeGradient = if (dark) {
            Brush.verticalGradient(
                listOf(
                    EchoGlassNight.copy(alpha = 0.62f),
                    EchoGlassInk.copy(alpha = 0.44f),
                    EchoGlassPanel.copy(alpha = 0.20f),
                    Color.Transparent,
                ),
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.36f),
                    EchoHomeMist.copy(alpha = 0.28f),
                    Color(0xFFEAF2FF).copy(alpha = 0.22f),
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
                    .widthIn(max = LocalEchoContentMaxWidth.current)
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .then(contentScroll)
                    .padding(start = horizontalPadding, end = horizontalPadding, top = topPadding),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (titleContent != null) {
                            titleContent()
                        } else if (showBrand) {
                            Text(stringResource(R.string.chrome_brand), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text(
                                title,
                                style = titleStyle,
                                fontWeight = FontWeight.Bold,
                                color = if (dark) Color.White.copy(alpha = 0.96f) else scheme.onSurface,
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (badgeContent != null) {
                            badgeContent()
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (dark) EchoGlassPanel.copy(alpha = 0.74f) else scheme.surface.copy(alpha = 0.50f),
                                border = BorderStroke(
                                    1.dp,
                                    if (dark) EchoDarkGlassBorder else EchoGlassBorder,
                                ),
                            ) {
                                Text(
                                    resolvedBadge,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (dark) Color.White.copy(alpha = 0.82f) else scheme.onSurface,
                                )
                            }
                        }
                        actions()
                    }
                }
                if (showBrand) {
                    Spacer(Modifier.height(headerGap))
                    Text(title, style = titleStyle, fontWeight = FontWeight.Bold, color = if (dark) Color.White.copy(alpha = 0.96f) else scheme.onSurface)
                }
                if (subtitle != null) {
                    Text(
                        subtitle,
                        color = if (dark) Color.White.copy(alpha = 0.74f) else scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(contentGap))
                content()
                if (scrollable) {
                    Spacer(Modifier.height(scrollBottomPadding))
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
 * 浠庝笓杈戝皝闈㈡彁鍙栫殑閰嶈壊锛岀敤浜庢矇娴稿紡璇︽儏椤电殑娓愬彉鑳屾櫙涓庡己璋冭壊銆?
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

        /** 娌℃湁灏侀潰鏃讹紝鐢ㄦ爣璇嗕覆鐢熸垚涓€涓ǔ瀹氫笖濂界湅鐨勯厤鑹层€?*/
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
    val effectivePerformanceMode = LocalEchoEffectivePerformanceMode.current
    if (effectivePerformanceMode.isLightweight) {
        return remember(seedKey) { ArtworkPalette.fromSeed(seedKey) }
    }
    val sampleSize = if (effectivePerformanceMode.isHighPerformance) 8 else 16
    val context = LocalContext.current
    val rewriteRevision = EchoArtworkUrlRewriteRegistry.revision
    val fetchUri = resolvedArtworkFetchUri(artworkUri)
    val requestHeaders = EchoArtworkRequestHeadersRegistry.headersFor(artworkUri)
    val palette by produceState(
        ArtworkPalette.fromSeed(seedKey),
        artworkUri,
        fetchUri,
        seedKey,
        sampleSize,
        rewriteRevision,
        requestHeaders,
    ) {
        value = withContext(Dispatchers.IO) {
            val bitmap = loadArtworkSwatch(
                context = context,
                artworkUri = fetchUri ?: artworkUri,
                sampleSize = sampleSize,
                headers = requestHeaders,
            )
            if (bitmap != null) {
                extractPalette(bitmap)
            } else {
                ArtworkPalette.fromSeed(seedKey)
            }
        }
    }
    return palette
}

private suspend fun loadArtworkSwatch(
    context: android.content.Context,
    artworkUri: String?,
    sampleSize: Int,
    headers: Map<String, String>,
): Bitmap? {
    if (artworkUri.isNullOrBlank()) return null
    val edge = if (sampleSize <= 8) 128 else 64
    val request = ImageRequest.Builder(context)
        .data(artworkUri)
        .size(edge, edge)
        .bitmapConfig(Bitmap.Config.ARGB_8888)
        .allowHardware(false)
        .memoryCacheKey(echoArtworkCacheKey(artworkUri, edge, highBitDepth = true))
        .crossfade(false)
        .apply {
            headers.forEach { (name, value) -> setHeader(name, value) }
        }
        .build()
    val result = context.imageLoader.execute(request)
    val drawable = (result as? SuccessResult)?.drawable ?: return null
    return drawable.toSoftwareBitmap()
}

private fun Drawable.toSoftwareBitmap(): Bitmap {
    val existing = (this as? BitmapDrawable)?.bitmap
    if (
        existing != null &&
        !existing.isRecycled &&
        existing.config != Bitmap.Config.HARDWARE
    ) {
        return existing
    }
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    setBounds(0, 0, width, height)
    draw(canvas)
    return output
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
            // 鍋忓ソ椴滆壋銆佷寒搴﹂€備腑鐨勫儚绱犱綔涓哄己璋冭壊
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
 * Apple Music 椋庢牸锛氭妸褰撳墠灏侀潰鏀惧ぇ妯＄硦鎴愭瘺鐜荤拑鑳屾櫙锛岄殢姝屽彉鑹诧紱鏃犲皝闈㈡垨浣庣増鏈洖閫€鍒板彇鑹叉笎鍙樸€?
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
    val effectivePerformanceMode = LocalEchoEffectivePerformanceMode.current
    val artworkMaxPixelSize = when {
        effectivePerformanceMode.isLightweight -> 512
        effectivePerformanceMode.isHighPerformance -> 1280
        else -> 768
    }
    val effectiveArtworkBlur = when {
        effectivePerformanceMode.isLightweight -> 0.dp
        effectivePerformanceMode.isHighPerformance -> artworkBlur
        else -> artworkBlur.coerceAtMost(28.dp)
    }
    val effectiveArtworkAlpha = if (effectivePerformanceMode.isBalanced) {
        artworkAlpha.coerceAtMost(0.52f)
    } else {
        artworkAlpha
    }
    val rewriteRevision = EchoArtworkUrlRewriteRegistry.revision
    val fetchUri = resolvedArtworkFetchUri(artworkUri)
    val highBitDepth = effectivePerformanceMode.isHighPerformance
    val artworkModel = remember(
        context,
        artworkUri,
        fetchUri,
        artworkMaxPixelSize,
        highBitDepth,
        rewriteRevision,
    ) {
        echoArtworkImageRequest(
            context = context,
            originalUri = artworkUri,
            fetchUri = fetchUri,
            maxPixelSize = artworkMaxPixelSize,
            highBitDepth = highBitDepth,
        )
    }
    Box(modifier = modifier.fillMaxSize()) {
        // 鍙栬壊搴曪紝淇濊瘉鏃犲皝闈?浣庝簬 API 31 鏃朵篃鏈夋矇娴歌壊
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
        if (!effectivePerformanceMode.isLightweight && !fetchUri.isNullOrBlank()) {
            AsyncImage(
                model = artworkModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(artworkScale)
                    .then(
                        if (effectiveArtworkBlur > 0.dp) {
                            Modifier.blur(effectiveArtworkBlur)
                        } else {
                            Modifier
                        },
                    )
                    .alpha(effectiveArtworkAlpha),
            )
        }
        // 鍘嬫殫鐨勬瘺鐜荤拑缃╋紝淇濊瘉鐧借壊鏂囧瓧涓庢帶浠跺彲璇?
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
