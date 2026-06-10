package app.echo.android.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

object EchoColors {
    // 深邃蓝黑 + Shakespeare 蓝：更现代的沉浸底色
    val Night = Color(0xFF0B1020)
    val Ink = Color(0xFF151B31)
    val Slate = Color(0xFF202744)
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
    outline = Color(0xFF4A4E5C),
    outlineVariant = Color(0xFF40486A),
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

val LocalEchoDensityScale = staticCompositionLocalOf { 1f }
val LocalEchoDarkTheme = staticCompositionLocalOf { false }

private val EchoOutfitFontFamily = FontFamily(
    Font(R.font.outfit_variable, weight = FontWeight.Light),
    Font(R.font.outfit_variable, weight = FontWeight.Normal),
    Font(R.font.outfit_variable, weight = FontWeight.Medium),
    Font(R.font.outfit_variable, weight = FontWeight.SemiBold),
    Font(R.font.outfit_variable, weight = FontWeight.Bold),
    Font(R.font.outfit_variable, weight = FontWeight.ExtraBold),
    Font(R.font.outfit_variable, weight = FontWeight.Black),
)

fun echoFontFamilyForMode(
    mode: String,
    importedFontFamily: FontFamily? = null,
): FontFamily = when (mode) {
    "outfit" -> EchoOutfitFontFamily
    "serif" -> FontFamily.Serif
    "monospace" -> FontFamily.Monospace
    "imported" -> importedFontFamily ?: FontFamily.SansSerif
    else -> FontFamily.SansSerif
}

private fun echoTypography(
    fontFamily: FontFamily,
    fontScale: Float,
): Typography = Typography().let { typography ->
    typography.copy(
        displayLarge = typography.displayLarge.echoFont(fontFamily, FontWeight.ExtraBold, fontScale),
        displayMedium = typography.displayMedium.echoFont(fontFamily, FontWeight.ExtraBold, fontScale),
        displaySmall = typography.displaySmall.echoFont(fontFamily, FontWeight.ExtraBold, fontScale),
        headlineLarge = typography.headlineLarge.echoFont(fontFamily, FontWeight.Bold, fontScale),
        headlineMedium = typography.headlineMedium.echoFont(fontFamily, FontWeight.Bold, fontScale),
        headlineSmall = typography.headlineSmall.echoFont(fontFamily, FontWeight.Bold, fontScale),
        titleLarge = typography.titleLarge.echoFont(fontFamily, FontWeight.Bold, fontScale),
        titleMedium = typography.titleMedium.echoFont(fontFamily, FontWeight.Bold, fontScale),
        titleSmall = typography.titleSmall.echoFont(fontFamily, FontWeight.Bold, fontScale),
        bodyLarge = typography.bodyLarge.echoFont(fontFamily, FontWeight.SemiBold, fontScale),
        bodyMedium = typography.bodyMedium.echoFont(fontFamily, FontWeight.SemiBold, fontScale),
        bodySmall = typography.bodySmall.echoFont(fontFamily, FontWeight.SemiBold, fontScale),
        labelLarge = typography.labelLarge.echoFont(fontFamily, FontWeight.Bold, fontScale),
        labelMedium = typography.labelMedium.echoFont(fontFamily, FontWeight.Bold, fontScale),
        labelSmall = typography.labelSmall.echoFont(fontFamily, FontWeight.Bold, fontScale),
    )
}

private fun TextStyle.echoFont(
    fontFamily: FontFamily,
    fontWeight: FontWeight,
    fontScale: Float,
): TextStyle = copy(
    fontFamily = fontFamily,
    fontWeight = fontWeight,
    fontSize = fontSize.scale(fontScale),
    lineHeight = lineHeight.scale(fontScale),
)

private fun TextUnit.scale(scale: Float): TextUnit =
    if (isSpecified) (value * scale).sp else this

@Composable
fun EchoMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontFamily: FontFamily = FontFamily.SansSerif,
    fontScale: Float = 1f,
    densityScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalEchoDensityScale provides densityScale.coerceIn(0.90f, 1.12f),
        LocalEchoDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) EchoDarkScheme else EchoLightScheme,
            typography = echoTypography(fontFamily, fontScale.coerceIn(0.88f, 1.18f)),
            content = content,
        )
    }
}
