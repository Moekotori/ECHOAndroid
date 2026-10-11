package app.echo.android.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import app.echo.android.design.ArtworkPalette
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoTheme

/** One flowing surface behind the cover and lyrics, without decoding another full-screen image. */
@Composable
internal fun NowPlayingBackdrop(
    palette: ArtworkPalette,
    reveal: () -> Float,
    modifier: Modifier = Modifier,
    animationsVisible: Boolean = true,
) {
    val dark = LocalEchoDarkTheme.current
    val mode = LocalEchoEffectivePerformanceMode.current
    val theme = echoTheme()
    val motionEnabled = animationsVisible && !mode.isLightweight
    val elapsed = rememberFluidBackdropTime(motionEnabled, fluidBackdropFps(mode))
    val wash = remember(palette, dark, theme) {
        val anchor = if (dark) theme.night else theme.bgTop
        palette.copy(
            vibrant = lerp(palette.vibrant, anchor, if (dark) 0.18f else 0.48f),
            deep = lerp(palette.deep, anchor, if (dark) 0.22f else 0.68f),
            soft = lerp(palette.soft, anchor, if (dark) 0.30f else 0.48f),
        )
    }
    val target = remember(wash) { FluidArtworkField(wash) }
    var layers by remember { mutableStateOf(FluidBackdropLayers(target)) }
    LaunchedEffect(target, motionEnabled) {
        if (target !== layers.current) {
            layers = FluidBackdropLayers(target, if (motionEnabled) layers.current else null, elapsed.value)
        } else if (!motionEnabled && layers.previous != null) {
            layers = FluidBackdropLayers(target)
        }
    }
    Box(modifier.drawWithCache {
        val veil = Brush.verticalGradient(
            0f to theme.night.copy(alpha = if (dark) 0.24f else 0.02f),
            0.46f to Color.Transparent,
            1f to theme.night.copy(alpha = if (dark) 0.62f else 0.16f),
        )
        onDrawBehind {
            // Time and page gestures invalidate drawing only, never the player or its lists.
            val time = elapsed.value
            val blend = if (motionEnabled) ((time - layers.changedAtMs) / 1600f).coerceIn(0f, 1f) else 1f
            val smoothBlend = blend * blend * (3f - 2f * blend)
            drawIntoCanvas { canvas ->
                if (blend < 1f) layers.previous?.draw(canvas.nativeCanvas, size.width, size.height, time, 1f)
                layers.current.draw(canvas.nativeCanvas, size.width, size.height, time,
                    if (layers.previous == null) 1f else smoothBlend)
            }
            drawRect(veil)
            drawRect(theme.night.copy(alpha = (if (dark) 0.14f else 0.05f) * reveal().coerceIn(0f, 1f)))
        }
    }.fillMaxSize())
}

// At most two small sets of shaders, including during rapid track changes.
private data class FluidBackdropLayers(
    val current: FluidArtworkField,
    val previous: FluidArtworkField? = null,
    val changedAtMs: Long = 0L,
)
