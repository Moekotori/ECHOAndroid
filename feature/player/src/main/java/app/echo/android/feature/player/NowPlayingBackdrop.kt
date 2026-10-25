package app.echo.android.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import app.echo.android.design.ArtworkColorGrid
import app.echo.android.design.ArtworkPalette
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoTheme
import app.echo.android.model.settings.EchoLyricsBackdrop
import app.echo.android.model.settings.EchoPlayerPageStyle
import kotlinx.coroutines.flow.collectLatest

/**
 * One surface behind the cover and lyrics, without decoding another full-screen image. [style] picks the
 * painter; styles that read the cover's color mosaic fall back to the fluid surface until [colorGrid] arrives.
 */
@Composable
internal fun NowPlayingBackdrop(
    palette: ArtworkPalette,
    reveal: () -> Float,
    modifier: Modifier = Modifier,
    animationsVisible: Boolean = true,
    style: EchoLyricsBackdrop = EchoLyricsBackdrop.Fluid,
    colorGrid: ArtworkColorGrid? = null,
    link: BackdropLink? = null,
) {
    val dark = LocalEchoDarkTheme.current
    // The full-bleed page styles pick their own field and dim it on the lyrics page.
    val pageStyle = LocalPlayerPageStyle.current
    val dense = pageStyle == EchoPlayerPageStyle.Halftone
    val lyricsDim = when (pageStyle) {
        EchoPlayerPageStyle.Halftone -> 0.62f
        EchoPlayerPageStyle.PixelMosaic -> 0.68f
        EchoPlayerPageStyle.Contours -> 0.5f
        EchoPlayerPageStyle.Dither -> 0.7f
        EchoPlayerPageStyle.GlyphField -> 0.62f
        else -> 0f
    }
    val mode = LocalEchoEffectivePerformanceMode.current
    val theme = echoTheme()
    val motionEnabled = animationsVisible && !mode.isLightweight
    // Styles that follow playback ease down while paused but keep drifting; the others never change speed.
    val playing = link?.playing?.value ?: true
    val followsPlayback = style.slowsWhenPaused
    val rate = remember { Animatable(1f) }
    LaunchedEffect(playing, followsPlayback) {
        rate.animateTo(if (playing || !followsPlayback) 1f else PausedClockRate, tween(900))
    }
    val elapsed = rememberFluidBackdropTime(motionEnabled, fluidBackdropFps(mode)) { rate.value }
    // A short kick on every lyric line change; drawing reads it, so only the backdrop is invalidated.
    val pulse = remember { Animatable(0f) }
    val pulseActive = link != null && style.reactsToLyrics && motionEnabled
    LaunchedEffect(link, pulseActive) {
        if (link == null || !pulseActive) {
            pulse.snapTo(0f)
            return@LaunchedEffect
        }
        var previous = link.lineIndex.value
        snapshotFlow { link.lineIndex.value }.collectLatest { index ->
            if (index == previous) return@collectLatest
            previous = index
            if (index < 0) return@collectLatest
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(PulseDecayMs, easing = FastOutSlowInEasing))
        }
    }
    val wash = remember(palette, dark, theme) {
        val anchor = if (dark) theme.night else theme.bgTop
        palette.copy(
            vibrant = lerp(palette.vibrant, anchor, if (dark) 0.06f else 0.34f),
            deep = lerp(palette.deep, anchor, if (dark) 0.10f else 0.50f),
            soft = lerp(palette.soft, anchor, if (dark) 0.10f else 0.34f),
        )
    }
    val washedGrid = remember(colorGrid, dark, theme) {
        colorGrid?.washedToward(if (dark) theme.night else theme.bgTop, if (dark) 0.06f else 0.34f)
    }
    val target: BackdropField = remember(wash, style, washedGrid, dense, pageStyle) {
        when {
            pageStyle == EchoPlayerPageStyle.PixelMosaic && washedGrid != null -> PixelMosaicBackdropField(wash.deep, washedGrid)
            pageStyle == EchoPlayerPageStyle.Contours && washedGrid != null -> ContourBackdropField(wash.deep, wash.vibrant, wash.soft, washedGrid)
            pageStyle == EchoPlayerPageStyle.Dither && washedGrid != null -> DitherBackdropField(wash.deep, wash.soft, washedGrid)
            pageStyle == EchoPlayerPageStyle.GlyphField && washedGrid != null -> GlyphBackdropField(wash.deep, washedGrid)
            style == EchoLyricsBackdrop.Mesh && washedGrid != null -> MeshBackdropField(wash.deep, washedGrid)
            style == EchoLyricsBackdrop.Halftone && washedGrid != null -> HalftoneBackdropField(wash.deep, washedGrid, dense)
            style == EchoLyricsBackdrop.Ridge && washedGrid != null -> RidgeBackdropField(wash.deep, washedGrid, dark)
            style == EchoLyricsBackdrop.Vinyl && washedGrid != null -> VinylBackdropField(wash.deep, washedGrid, dark)
            style == EchoLyricsBackdrop.Iso && washedGrid != null -> IsoBackdropField(wash.deep, washedGrid, dark)
            else -> FluidArtworkField(wash)
        }
    }
    var layers by remember { mutableStateOf(FluidBackdropLayers(target)) }
    LaunchedEffect(target, motionEnabled) {
        if (target !== layers.current) {
            layers = FluidBackdropLayers(target, if (motionEnabled) layers.current else null, elapsed.value)
        } else if (!motionEnabled && layers.previous != null) {
            layers = FluidBackdropLayers(target)
        }
    }
    Box(modifier.drawWithCache {
        val titleVeil = Brush.verticalGradient(0.38f to Color.Transparent, 0.8f to Color.Black.copy(alpha = 0.78f))
        val veil = Brush.verticalGradient(
            0f to theme.night.copy(alpha = if (dark) 0.12f else 0.02f),
            0.46f to Color.Transparent,
            1f to theme.night.copy(alpha = if (dark) 0.38f else 0.12f),
        )
        onDrawBehind {
            // Time and page gestures invalidate drawing only, never the player or its lists.
            val time = elapsed.value
            val blend = if (motionEnabled) ((time - layers.changedAtMs) / 1600f).coerceIn(0f, 1f) else 1f
            val smoothBlend = blend * blend * (3f - 2f * blend)
            val kick = pulse.value
            drawIntoCanvas { canvas ->
                if (blend < 1f) layers.previous?.draw(canvas.nativeCanvas, size.width, size.height, time, 1f, kick)
                layers.current.draw(canvas.nativeCanvas, size.width, size.height, time,
                    if (layers.previous == null) 1f else smoothBlend, kick)
            }
            drawRect(veil)
            drawRect(theme.night.copy(alpha = (if (dark) 0.06f else 0.04f) * reveal().coerceIn(0f, 1f)))
            if (lyricsDim > 0f) drawRect(Color.Black.copy(alpha = lyricsDim * reveal().coerceIn(0f, 1f)))
            if (pageStyle.isFullBleed && pageStyle != EchoPlayerPageStyle.Halftone) drawRect(titleVeil, alpha = 1f - reveal().coerceIn(0f, 1f))
        }
    }.fillMaxSize())
}

private const val PausedClockRate = 0.2f
private const val PulseDecayMs = 650

// At most two small sets of shaders, including during rapid track changes.
private data class FluidBackdropLayers(
    val current: BackdropField,
    val previous: BackdropField? = null,
    val changedAtMs: Long = 0L,
)

private val EchoPlayerPageStyle.isFullBleed: Boolean get() = id.isFullBleedStyle()
