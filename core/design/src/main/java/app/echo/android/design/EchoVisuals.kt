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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
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

fun progressFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
