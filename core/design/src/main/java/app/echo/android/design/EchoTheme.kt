package app.echo.android.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

object EchoColors {
    // Roon 风格中性炭灰 + Shakespeare 蓝
    val Night = Color(0xFF161618)
    val Ink = Color(0xFF222226)
    val Slate = Color(0xFF2E2E33)
    val DeepBlue = Color(0xFF3E7BA8)
    val Brass = Color(0xFFE1A33A)
    val Coral = Color(0xFFD7675D)
    val Sky = Color(0xFF62B0D9)
    val RoonBlue = Color(0xFF62B0D9)
    val Paper = Color(0xFFF1F1F3)
    val Mist = Color(0xFFEEF0F6)
    val Smoke = Color(0xFFA8A8AE)
}

private val EchoDarkScheme = darkColorScheme(
    primary = EchoColors.RoonBlue,
    onPrimary = Color(0xFF06121A),
    secondary = EchoColors.RoonBlue,
    onSecondary = Color(0xFF06121A),
    tertiary = EchoColors.Coral,
    background = EchoColors.Night,
    onBackground = EchoColors.Paper,
    surface = EchoColors.Ink,
    onSurface = EchoColors.Paper,
    surfaceVariant = EchoColors.Slate,
    onSurfaceVariant = EchoColors.Smoke,
    outline = Color(0xFF4A4A52),
    outlineVariant = Color(0xFF323238),
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

private val EchoReadableFontFamily = FontFamily.SansSerif

private val EchoTypography = Typography().let { typography ->
    typography.copy(
        displayLarge = typography.displayLarge.copy(fontFamily = EchoReadableFontFamily, fontWeight = FontWeight.SemiBold),
        displayMedium = typography.displayMedium.copy(fontFamily = EchoReadableFontFamily, fontWeight = FontWeight.SemiBold),
        displaySmall = typography.displaySmall.copy(fontFamily = EchoReadableFontFamily, fontWeight = FontWeight.SemiBold),
        headlineLarge = typography.headlineLarge.copy(fontFamily = EchoReadableFontFamily, fontWeight = FontWeight.SemiBold),
        headlineMedium = typography.headlineMedium.copy(fontFamily = EchoReadableFontFamily, fontWeight = FontWeight.SemiBold),
        headlineSmall = typography.headlineSmall.copy(fontFamily = EchoReadableFontFamily, fontWeight = FontWeight.SemiBold),
        titleLarge = typography.titleLarge.copy(fontFamily = EchoReadableFontFamily, fontWeight = FontWeight.SemiBold),
        titleMedium = typography.titleMedium.copy(fontFamily = EchoReadableFontFamily, fontWeight = FontWeight.Medium),
        titleSmall = typography.titleSmall.copy(fontFamily = EchoReadableFontFamily, fontWeight = FontWeight.Medium),
        bodyLarge = typography.bodyLarge.copy(fontFamily = EchoReadableFontFamily, fontWeight = FontWeight.Normal),
        bodyMedium = typography.bodyMedium.copy(fontFamily = EchoReadableFontFamily, fontWeight = FontWeight.Normal),
        bodySmall = typography.bodySmall.copy(fontFamily = EchoReadableFontFamily, fontWeight = FontWeight.Normal),
        labelLarge = typography.labelLarge.copy(fontFamily = EchoReadableFontFamily, fontWeight = FontWeight.Medium),
        labelMedium = typography.labelMedium.copy(fontFamily = EchoReadableFontFamily, fontWeight = FontWeight.Medium),
        labelSmall = typography.labelSmall.copy(fontFamily = EchoReadableFontFamily, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun EchoMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) EchoDarkScheme else EchoLightScheme,
        typography = EchoTypography,
        content = content,
    )
}
