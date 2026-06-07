package app.echo.android.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object EchoColors {
    val Night = Color(0xFF0B0B12)
    val Ink = Color(0xFF181923)
    val Slate = Color(0xFF303342)
    val DeepBlue = Color(0xFF185A92)
    val Brass = Color(0xFFE1A33A)
    val Coral = Color(0xFFD7675D)
    val Sky = Color(0xFF67A8F2)
    val Paper = Color(0xFFFBFBFE)
    val Mist = Color(0xFFEEF0F6)
    val Smoke = Color(0xFFC9CCD6)
}

private val EchoDarkScheme = darkColorScheme(
    primary = EchoColors.Sky,
    onPrimary = EchoColors.Night,
    secondary = EchoColors.Brass,
    onSecondary = EchoColors.Night,
    tertiary = EchoColors.Coral,
    background = EchoColors.Night,
    onBackground = EchoColors.Paper,
    surface = EchoColors.Ink,
    onSurface = EchoColors.Paper,
    surfaceVariant = EchoColors.Slate,
    onSurfaceVariant = EchoColors.Smoke,
    outline = Color(0xFF6B7082),
    outlineVariant = Color(0xFF3B3E4E),
)

private val EchoLightScheme = lightColorScheme(
    primary = EchoColors.DeepBlue,
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFB84C45),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF9B6200),
    background = EchoColors.Paper,
    onBackground = Color(0xFF171822),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171822),
    surfaceVariant = EchoColors.Mist,
    onSurfaceVariant = EchoColors.Ink,
    outline = Color(0xFF747887),
    outlineVariant = Color(0xFFDDE0EA),
)

@Composable
fun EchoMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) EchoDarkScheme else EchoLightScheme,
        content = content,
    )
}
