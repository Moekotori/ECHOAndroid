package app.echo.android.feature.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoPageSection
import app.echo.android.model.settings.EchoLyricsPageStyle

/** A small sample stays visible while editing a single visual category. */
@Composable
internal fun LyricsSettingsPreview(
    pageStyle: EchoLyricsPageStyle,
    fontFamily: FontFamily?,
    fontScale: Float,
    colorMode: String,
    alignment: String,
    lineSpacing: Float,
    backgroundDim: Float,
    highlightEnabled: Boolean,
    highlightIntensity: Float,
    motionMode: String,
) {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val previewScale = animateFloatAsState(
        targetValue = when (motionMode) { "stage" -> 1.025f; "calm" -> 1f; else -> 1.012f },
        animationSpec = tween(if (lightweight) 0 else 240, easing = EchoMotion.Silk),
        label = "lyrics-preview-scale",
    )
    LyricsPageTheme(pageStyle) {
        val scheme = MaterialTheme.colorScheme
        val accent = lyricsColorForMode(colorMode)
        val emphasis = if (highlightEnabled) (0.65f + highlightIntensity.coerceIn(0.45f, 1.35f) * 0.24f).coerceAtMost(1f) else 1f
        Column(
            Modifier.fillMaxWidth().echoPageSection()
                .background(scheme.background.copy(alpha = 0.55f + backgroundDim.coerceIn(0f, 0.78f) * 0.4f))
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = lyricsHorizontalAlignment(alignment),
        ) {
            if (alignment == "vertical") {
                VerticalLyricText(
                    line = app.echo.android.model.lyrics.EchoLyricLine(-1, text = stringResource(R.string.lyrics_vertical_preview)),
                    lineEndMs = null, active = false, position = androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0L) },
                    wordHighlight = false, estimatedHighlight = false, fontFamily = fontFamily, fontScale = fontScale * 0.7f,
                    spacing = lineSpacing, color = accent, highlight = accent,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                )
            } else Text(
                text = stringResource(when (motionMode) {
                    "stage" -> R.string.feature_player_each_line_lifts_with_the_beat_ddb972
                    "calm" -> R.string.feature_player_lyrics_rest_quietly_in_the_center_810bd7
                    else -> R.string.feature_player_lyrics_breathe_naturally_with_playback_c6b1df
                }),
                modifier = Modifier.fillMaxWidth().graphicsLayer {
                    scaleX = previewScale.value
                    scaleY = previewScale.value
                },
                color = accent.copy(alpha = emphasis),
                fontFamily = fontFamily,
                fontSize = (18f * fontScale.coerceIn(0.50f, 1.28f)).sp,
                lineHeight = ((18f + 6f * lineSpacing.coerceIn(0.50f, 1.38f)) * fontScale.coerceIn(0.50f, 1.28f)).sp,
                fontWeight = FontWeight.Bold,
                textAlign = lyricsTextAlign(alignment),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
