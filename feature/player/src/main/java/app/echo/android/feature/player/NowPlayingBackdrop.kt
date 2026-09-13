package app.echo.android.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithCache
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkPalette
import app.echo.android.design.BlurredArtworkBackground
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.echoTheme

@Composable
internal fun NowPlayingBackdrop(
    artworkUri: String?,
    palette: ArtworkPalette,
    reveal: () -> Float,
    modifier: Modifier = Modifier,
) {
    if (!LocalEchoDarkTheme.current) {
        val scheme = MaterialTheme.colorScheme
        // Preserve a light reading surface while letting the current cover tint it.
        val lightWash = palette.copy(
            vibrant = lerp(scheme.surface, palette.vibrant, 0.12f),
            deep = lerp(scheme.surface, palette.soft, 0.10f),
            soft = lerp(scheme.surface, palette.soft, 0.08f),
        )
        BlurredArtworkBackground(
            artworkUri = artworkUri,
            palette = lightWash,
            modifier = modifier,
            artworkScale = 1.20f,
            artworkBlur = 28.dp,
            artworkAlpha = 0.16f,
            overlayStartAlpha = 0.54f,
            overlayMidAlpha = 0.66f,
            overlayEndAlpha = 0.90f,
        )
        return
    }
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val theme = echoTheme()
    // Read page progress only when updating the layer/drawing the veil. Keep image
    // composition and the blur radius stable throughout a horizontal gesture.
    Box(modifier = modifier) {
        BlurredArtworkBackground(
            artworkUri = artworkUri,
            palette = palette.asNowPlayingWash(echoTheme().night),
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val scale = if (lightweight) 1f else 1f + 0.06f * reveal()
                scaleX = scale
                scaleY = scale
            },
            artworkScale = 1.16f,
            artworkBlur = 28.dp,
            artworkAlpha = 0.58f,
            overlayStartAlpha = 0.46f,
            overlayMidAlpha = 0.58f,
            overlayEndAlpha = 0.90f,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(170.dp)
                .background(
                    Brush.verticalGradient(
                        0f to theme.night.copy(alpha = 0.34f),
                        0.48f to theme.ink.copy(alpha = 0.16f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Box(Modifier.fillMaxSize().drawWithCache {
            onDrawBehind {
                drawRect(theme.night.copy(alpha = 0.10f * reveal().coerceIn(0f, 1f)))
            }
        })
    }
}

private fun ArtworkPalette.asNowPlayingWash(night: Color): ArtworkPalette {
    return copy(
        vibrant = lerp(vibrant, night, 0.54f),
        deep = lerp(deep, night, 0.22f),
        soft = lerp(soft, night, 0.62f),
    )
}
