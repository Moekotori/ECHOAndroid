package app.echo.android.feature.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon as ChromeIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoChromeColors

internal const val MiniPlayerSwipeCommitFraction = 0.22f

/** Behind the moving track row: input and the skip threshold remain owned by MiniPlayer. */
@Composable
internal fun MiniPlayerSwipeFeedback(
    offset: () -> Float,
    width: () -> Float,
    modifier: Modifier = Modifier,
) {
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val chrome = echoChromeColors()
    val tint = chrome.content
    val surface = chrome.surface
    Box(modifier.clearAndSetSemantics {}) {
        // Directions are physical, matching the player's existing left=next gesture in RTL too.
        for (next in listOf(false, true)) {
            val direction = if (next) -1f else 1f
            Row(
                modifier = Modifier
                    .align(if (next) AbsoluteAlignment.CenterRight else AbsoluteAlignment.CenterLeft)
                    .widthIn(max = 72.dp)
                    .graphicsLayer {
                        val distance = direction * offset()
                        val progress = (distance / (width().coerceAtLeast(1f) * MiniPlayerSwipeCommitFraction)).coerceIn(0f, 1f)
                        alpha = ((progress - 0.12f) / 0.88f).coerceIn(0f, 1f)
                        scaleX = if (lightweight) 1f else 0.92f + 0.08f * progress
                        scaleY = scaleX
                    }
                    .drawWithCache {
                        val wash = Brush.radialGradient(
                            listOf(surface.copy(alpha = 0.92f), surface.copy(alpha = 0.6f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.maxDimension.coerceAtLeast(1f),
                        )
                        onDrawBehind { drawRect(wash) }
                    }
                    .padding(horizontal = 3.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChromeIcon(
                    if (next) PlayerControlIcons.Next else PlayerControlIcons.Previous,
                    contentDescription = null, tint = tint, modifier = Modifier.size(15.dp),
                )
                Text(
                    stringResource(if (next) R.string.feature_player_next_d67904 else R.string.feature_player_previous_af0264),
                    color = tint, style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
