package app.echo.android.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ripple
import androidx.compose.material3.SwitchColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A 46 × 28 dp capsule inside a 48 dp target. A null callback leaves semantics and input
 * to the containing toggleable row, avoiding a second accessibility stop.
 */
@Composable
fun EchoSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tokens = echoTheme()
    val colors = remember(tokens) { echoSwitchColors(tokens) }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val progress = animateFloatAsState(
        if (checked) 1f else 0f,
        tween(if (lightweight) 0 else 180), label = "echo-switch-position",
    )
    val haptics = rememberEchoHapticPerformer()
    val input = if (onCheckedChange == null) Modifier else Modifier.toggleable(
        value = checked, enabled = enabled, role = Role.Switch,
        interactionSource = interactionSource, indication = ripple(bounded = false, radius = 24.dp),
        onValueChange = { haptics.tick(); onCheckedChange(it) },
    )
    Canvas(modifier.size(48.dp).then(input)) {
        val amount = progress.value
        val track = lerp(
            if (enabled) colors.uncheckedTrackColor else colors.disabledUncheckedTrackColor,
            if (enabled) colors.checkedTrackColor else colors.disabledCheckedTrackColor, amount,
        )
        val thumb = lerp(
            if (enabled) colors.uncheckedThumbColor else colors.disabledUncheckedThumbColor,
            if (enabled) colors.checkedThumbColor else colors.disabledCheckedThumbColor, amount,
        )
        val width = 46.dp.toPx()
        val height = 28.dp.toPx()
        val left = (size.width - width) / 2f
        drawRoundRect(track, Offset(left, center.y - height / 2f), Size(width, height), CornerRadius(height / 2f))
        val direction = if (layoutDirection == LayoutDirection.Rtl) 1f - amount else amount
        val thumbCenter = Offset(left + 14.dp.toPx() + 18.dp.toPx() * direction, center.y)
        drawCircle(Color.Black.copy(alpha = if (enabled) 0.12f else 0.04f), 11.dp.toPx(), thumbCenter + Offset(0f, 1.dp.toPx()))
        drawCircle(thumb, (if (enabled && pressed.value) 12f else 11f).dp.toPx(), thumbCenter)
    }
}

internal fun echoSwitchColors(tokens: EchoThemeTokens): SwitchColors {
    val surface = tokens.surface
    val checkedThumb = Color.White
    val checkedTrack = lerp(tokens.accentDeep, Color.Black, 0.22f)
    val uncheckedThumb = if (tokens.dark) Color.White else tokens.heading
    val uncheckedTrack = if (tokens.dark) {
        Color.White.copy(alpha = 0.22f).compositeOver(tokens.ink)
    } else {
        tokens.heading.copy(alpha = 0.16f).compositeOver(tokens.surface)
    }
    return SwitchColors(
        checkedThumbColor = checkedThumb,
        checkedTrackColor = checkedTrack,
        checkedBorderColor = Color.Transparent,
        checkedIconColor = checkedThumb,
        uncheckedThumbColor = uncheckedThumb,
        uncheckedTrackColor = uncheckedTrack,
        uncheckedBorderColor = Color.Transparent,
        uncheckedIconColor = uncheckedThumb,
        disabledCheckedThumbColor = checkedThumb.copy(alpha = DisabledSwitchAlpha).compositeOver(surface),
        disabledCheckedTrackColor = checkedTrack.copy(alpha = DisabledSwitchAlpha).compositeOver(surface),
        disabledCheckedBorderColor = Color.Transparent,
        disabledCheckedIconColor = checkedThumb.copy(alpha = DisabledSwitchAlpha).compositeOver(surface),
        disabledUncheckedThumbColor = uncheckedThumb.copy(alpha = DisabledSwitchAlpha).compositeOver(surface),
        disabledUncheckedTrackColor = uncheckedTrack.copy(alpha = DisabledSwitchAlpha).compositeOver(surface),
        disabledUncheckedBorderColor = Color.Transparent,
        disabledUncheckedIconColor = uncheckedThumb.copy(alpha = DisabledSwitchAlpha).compositeOver(surface),
    )
}

private const val DisabledSwitchAlpha = 0.38f
