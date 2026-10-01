package app.echo.android.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** App chrome uses neutral ink; changing the music palette must not recolor its controls. */
@Immutable
data class EchoChromeColors(
    val surface: Color,
    val content: Color,
    val secondary: Color,
    val separator: Color,
    val onContent: Color,
)

private val LightChrome = EchoChromeColors(
    surface = Color.White,
    content = Color(0xFF242529),
    secondary = Color(0xFF74767D),
    separator = Color(0xFFECEDEF),
    onContent = Color.White,
)

private val DarkChrome = EchoChromeColors(
    surface = Color(0xFF18191C),
    content = Color(0xFFF2F3F5),
    secondary = Color(0xFF9C9FA6),
    separator = Color(0xFF2C2E33),
    onContent = Color(0xFF18191C),
)

@Composable
fun echoChromeColors(): EchoChromeColors = if (LocalEchoDarkTheme.current) DarkChrome else LightChrome
