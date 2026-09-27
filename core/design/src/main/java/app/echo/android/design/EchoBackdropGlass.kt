package app.echo.android.design

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** Live glass for floating controls. Foreground content is drawn after the effect. */
@Composable
fun Modifier.echoBackdropGlass(cornerRadius: Dp, elevation: Dp = 6.dp): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val backdrop = LocalEchoGlassBackdrop.current
    if (backdrop == null || Build.VERSION.SDK_INT < 31) return echoFrostedGlass(shape, elevation)
    val dark = LocalEchoDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    val highQuality = LocalEchoEffectivePerformanceMode.current.isHighPerformance
    val sample = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val wash = remember(dark, scheme) {
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = if (dark) 0.12f else 0.34f),
            0.48f to scheme.surface.copy(alpha = if (dark) 0.38f else 0.46f),
            1f to scheme.surface.copy(alpha = if (dark) 0.58f else 0.60f),
        )
    }
    val rim = remember(dark, scheme) {
        Brush.linearGradient(
            0f to Color.White.copy(alpha = if (dark) 0.58f else 0.96f),
            0.26f to Color.White.copy(alpha = if (dark) 0.15f else 0.48f),
            0.52f to scheme.onSurface.copy(alpha = if (dark) 0.04f else 0.12f),
            0.78f to scheme.primary.copy(alpha = 0.16f),
            1f to Color.White.copy(alpha = if (dark) 0.36f else 0.78f),
        )
    }
    return this
        .echoGlassPress()
        .shadow(elevation, shape, clip = false)
        .clip(shape)
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawWithCache {
            val blur = (if (highQuality) 7.dp else 5.dp).toPx()
            val bend = (if (highQuality) 7.dp else 5.dp).toPx()
            val inset = ceil(blur * 3f + bend)
            val sampleSize = IntSize(
                ceil(size.width + inset * 2f).toInt(),
                ceil(size.height + inset * 2f).toInt(),
            )
            // Provider guarantees API 31+ and hardware rendering. Only this small
            // control-sized layer is rasterized; the full-page sources are display lists.
            sample.renderEffect = glassBackdropEffect(
                size.width, size.height, inset,
                cornerRadius.toPx().coerceAtMost(size.minDimension / 2f), blur, bend,
            )
            sample.clip = true
            onDrawBehind {
                if (backdrop.background.ready && backdrop.content.ready) {
                    val backgroundOffset = backdrop.background.origin - origin
                    val pageOffset = backdrop.content.origin - origin
                    sample.record(size = sampleSize) {
                        translate(inset + backgroundOffset.x, inset + backgroundOffset.y) {
                            drawLayer(backdrop.background.layer)
                        }
                        translate(inset + pageOffset.x, inset + pageOffset.y) {
                            drawLayer(backdrop.content.layer)
                        }
                    }
                    translate(-inset, -inset) { drawLayer(sample) }
                } else {
                    drawRect(scheme.surface)
                }
            }
        }
        .background(wash)
        .border(BorderStroke(0.8.dp, rim), shape)
}
