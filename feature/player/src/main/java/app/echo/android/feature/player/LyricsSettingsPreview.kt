package app.echo.android.feature.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/** Pinned above the controls: changing typography stays visible even while scrolling the settings. */
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
    val lineGap by animateDpAsState(
        targetValue = (6f * lineSpacing.coerceIn(0.50f, 1.38f)).dp,
        animationSpec = tween(if (lightweight) 0 else 180, easing = EchoMotion.Silk),
        label = "lyrics-preview-spacing",
    )
    LyricsPageTheme(pageStyle) {
        val scheme = MaterialTheme.colorScheme
        val accent = lyricsColorForMode(colorMode)
        val emphasis = if (highlightEnabled) (0.65f + highlightIntensity.coerceIn(0.45f, 1.35f) * 0.24f).coerceAtMost(1f) else 1f
        Column(
            Modifier.fillMaxWidth().echoPageSection()
                .background(scheme.background.copy(alpha = 0.55f + backgroundDim.coerceIn(0f, 0.78f) * 0.4f))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = lyricsHorizontalAlignment(alignment),
            verticalArrangement = Arrangement.spacedBy(lineGap),
        ) {
            if (alignment == "vertical") {
                VerticalLyricText(
                    line = app.echo.android.model.lyrics.EchoLyricLine(-1, text = stringResource(R.string.lyrics_vertical_preview)),
                    lineEndMs = null, active = false, position = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0L) },
                    wordHighlight = false, estimatedHighlight = false, fontFamily = fontFamily, fontScale = fontScale * 0.7f,
                    spacing = lineSpacing, color = accent, highlight = accent,
                    modifier = Modifier.fillMaxWidth().then(Modifier.height(100.dp)),
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
                fontSize = (20f * fontScale.coerceIn(0.50f, 1.28f)).sp,
                lineHeight = (26f * fontScale.coerceIn(0.50f, 1.28f)).sp,
                fontWeight = FontWeight.Bold,
                textAlign = lyricsTextAlign(alignment),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.feature_player_translation_romaji_appear_when_the_current_lyrics_include_3b41d9),
                modifier = Modifier.fillMaxWidth(),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = lyricsTextAlign(alignment),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
