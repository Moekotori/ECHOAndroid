package app.echo.android.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.echo.android.design.LocalEchoDarkTheme

internal data class RadioPlayerColors(
    val background: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val rule: Color,
)

private val LightRadioColors = RadioPlayerColors(
    background = Color(0xFFFAF7F2), ink = Color(0xFF242830),
    muted = Color(0xFF77646B), accent = Color(0xFF78364B), rule = Color(0xFFB49A9F),
)
private val DarkRadioColors = RadioPlayerColors(
    background = Color(0xFF181B22), ink = Color(0xFFF4EFEA),
    muted = Color(0xFFB8B2B7), accent = Color(0xFFD7A5B6), rule = Color(0xFF68616C),
)

@Composable
internal fun radioPlayerColors(): RadioPlayerColors =
    if (LocalEchoDarkTheme.current) DarkRadioColors else LightRadioColors
