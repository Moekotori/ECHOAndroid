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
import androidx.compose.ui.unit.dp
import app.echo.android.design.ArtworkPalette
import app.echo.android.design.BlurredArtworkBackground
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.echoTheme
import kotlin.math.roundToInt

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
    // 每帧变化的 reveal 只在这里读取,横滑时重组范围被限制在背景层
    val lyricsReveal = reveal()
    // 模糊半径量化为 5 档,避免每帧重建 RenderEffect
    val blurStep = (lyricsReveal * 4f).roundToInt()
    Box(modifier = modifier) {
        BlurredArtworkBackground(
            artworkUri = artworkUri,
            palette = palette.asNowPlayingWash(echoTheme().night),
            modifier = Modifier.fillMaxSize(),
            artworkScale = 1.16f + 0.10f * lyricsReveal,
            artworkBlur = 24.dp + 2.dp * blurStep,
            artworkAlpha = 0.58f - 0.08f * lyricsReveal,
            overlayStartAlpha = 0.46f + 0.12f * lyricsReveal,
            overlayMidAlpha = 0.58f + 0.10f * lyricsReveal,
            overlayEndAlpha = 0.90f,
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(170.dp)
                .background(
                    Brush.verticalGradient(
                        0f to echoTheme().night.copy(alpha = 0.34f - 0.08f * lyricsReveal),
                        0.48f to echoTheme().ink.copy(alpha = 0.16f - 0.04f * lyricsReveal),
                        1f to Color.Transparent,
                    ),
                ),
        )

    }
}

private fun ArtworkPalette.asNowPlayingWash(night: Color): ArtworkPalette {
    return copy(
        vibrant = lerp(vibrant, night, 0.54f),
        deep = lerp(deep, night, 0.22f),
        soft = lerp(soft, night, 0.62f),
    )
}
