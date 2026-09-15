package app.echo.android.design

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * Material 3's default switch uses a 2.dp outline and a 16.dp thumb when off. On Echo's dark
 * surfaces that reads as a hollow wireframe. This wrapper keeps a filled track and a full-size
 * thumb in both states.
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
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        thumbContent = { Box(Modifier.size(SwitchDefaults.IconSize)) },
    )
}

internal fun echoSwitchColors(tokens: EchoThemeTokens): SwitchColors {
    val surface = tokens.surface
    val checkedThumb = tokens.onAccent
    val checkedTrack = tokens.accent
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
