package app.echo.android.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Keeps Material's gestures, keyboard, RTL, step snapping and accessibility semantics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    neutralValue: Float? = null,
) {
    val source = remember { MutableInteractionSource() }
    Slider(
        value = value, onValueChange = onValueChange, modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled, valueRange = valueRange, steps = steps,
        onValueChangeFinished = onValueChangeFinished, interactionSource = source,
        thumb = { EchoSliderThumb(source, enabled) },
        track = { state ->
            EchoSliderRail(
                startFraction = { 0f }, endFraction = { controlFraction(state.value, valueRange) },
                enabled = enabled, steps = steps, neutralFraction = neutralValue?.let { controlFraction(it, valueRange) },
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val startSource = remember { MutableInteractionSource() }
    val endSource = remember { MutableInteractionSource() }
    RangeSlider(
        value = value, onValueChange = onValueChange, modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled, valueRange = valueRange, steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        startInteractionSource = startSource, endInteractionSource = endSource,
        startThumb = { EchoSliderThumb(startSource, enabled) },
        endThumb = { EchoSliderThumb(endSource, enabled) },
        track = { state ->
            EchoSliderRail(
                startFraction = { controlFraction(state.activeRangeStart, valueRange) },
                endFraction = { controlFraction(state.activeRangeEnd, valueRange) },
                enabled = enabled, steps = steps,
            )
        },
    )
}

@Composable
private fun EchoSliderThumb(source: MutableInteractionSource, enabled: Boolean) {
    val pressed = source.collectIsPressedAsState()
    val dragged = source.collectIsDraggedAsState()
    val focused = source.collectIsFocusedAsState()
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary.copy(alpha = if (enabled) 1f else 0.38f)
    val face = scheme.surface
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    // A fixed slot avoids changing the value-to-pixel mapping while the thumb is pressed.
    Canvas(Modifier.size(24.dp)) {
        drawEchoControlThumb(center, accent, face,
            engaged = focused.value || (!lightweight && (pressed.value || dragged.value)), enabled = enabled)
    }
}

@Composable
private fun EchoSliderRail(
    startFraction: () -> Float,
    endFraction: () -> Float,
    enabled: Boolean,
    steps: Int,
    neutralFraction: Float? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary.copy(alpha = if (enabled) 1f else 0.38f)
    val inactive = scheme.onSurface.copy(alpha = if (enabled) 0.14f else 0.07f)
    val marker = scheme.onSurfaceVariant.copy(alpha = if (enabled) 0.55f else 0.20f)
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        val rtl = layoutDirection == LayoutDirection.Rtl
        fun point(fraction: Float) = Offset(size.width * if (rtl) 1f - fraction else fraction, center.y)
        val low = startFraction()
        val high = endFraction()
        drawEchoControlRail(point(0f), point(1f), point(low), point(high), accent, inactive)
        if (steps in 1..60) {
            for (index in 1..steps) {
                val fraction = index.toFloat() / (steps + 1)
                drawCircle(if (fraction in low..high) scheme.surface.copy(alpha = 0.6f) else marker,
                    0.8.dp.toPx(), point(fraction))
            }
        }
        if (neutralFraction != null && neutralFraction > 0f && neutralFraction < 1f) {
            val x = point(neutralFraction).x
            drawLine(marker, Offset(x, center.y - 6.dp.toPx()), Offset(x, center.y + 6.dp.toPx()),
                1.dp.toPx(), StrokeCap.Round)
        }
    }
}

private fun controlFraction(value: Float, range: ClosedFloatingPointRange<Float>): Float {
    val span = range.endInclusive - range.start
    return if (span > 0f) ((value - range.start) / span).coerceIn(0f, 1f) else 0f
}
