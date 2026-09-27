package app.echo.android.design

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

/**
 * A compact, filled switch that keeps the thumb light and the active track in the current theme.
 * The surrounding settings row provides the full touch target where this is used as a trailing icon.
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
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.scale(0.88f),
        enabled = enabled,
        colors = colors,
    )
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
