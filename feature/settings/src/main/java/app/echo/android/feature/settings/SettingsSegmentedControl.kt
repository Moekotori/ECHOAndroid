package app.echo.android.feature.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoEffectivePerformanceMode

internal data class SettingsSegment(
    val label: String,
    val selected: Boolean,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * A small, always-visible exclusive choice. The filled pill glides to the chosen segment and the
 * label colours fade with it; lightweight mode switches at once.
 */
@Composable
internal fun SettingsSegmentedControl(
    segments: List<SettingsSegment>,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val selectedIndex = segments.indexOfFirst { it.selected }
    // The pill position and its fade are read while drawing, so a glide never recomposes the labels.
    val position = animateFloatAsState(
        targetValue = selectedIndex.coerceAtLeast(0).toFloat(),
        animationSpec = if (lightweight) snap() else EchoMotion.silkFloat(EchoMotion.TabMs),
        label = "segment-pill-position",
    )
    val presence = animateFloatAsState(
        targetValue = if (selectedIndex >= 0) 1f else 0f,
        animationSpec = if (lightweight) snap() else tween(EchoMotion.FadeMs, easing = EchoMotion.Silk),
        label = "segment-pill-presence",
    )
    val pillColor = scheme.primaryContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(3.dp)
            .drawBehind {
                val count = segments.size
                if (count == 0 || presence.value <= 0.001f) return@drawBehind
                val gap = 3.dp.toPx()
                val width = (size.width - gap * (count - 1)) / count
                drawRoundRect(
                    color = pillColor.copy(alpha = pillColor.alpha * presence.value),
                    topLeft = Offset(position.value * (width + gap), 0f),
                    size = Size(width, size.height),
                    cornerRadius = CornerRadius(11.dp.toPx()),
                )
            }
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        segments.forEach { segment ->
            val labelColor by animateColorAsState(
                targetValue = if (segment.selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                animationSpec = if (lightweight) snap() else tween(EchoMotion.FadeMs, easing = EchoMotion.Silk),
                label = "segment-label",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .selectable(
                        selected = segment.selected,
                        enabled = segment.enabled,
                        role = Role.RadioButton,
                        onClick = segment.onClick,
                    )
                    .alpha(if (segment.enabled || segment.selected) 1f else 0.5f)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    segment.label,
                    color = labelColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (segment.selected) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
