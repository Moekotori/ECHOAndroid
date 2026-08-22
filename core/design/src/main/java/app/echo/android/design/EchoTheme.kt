package app.echo.android.design

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import app.echo.android.model.settings.EchoEffectivePerformanceMode

object EchoColors {
    // 深色模式走干净 graphite 底色，控制色用低饱和粉灰，避免蓝紫玻璃堆出脏感。
    val Night = Color(0xFF19191D)
    val Ink = Color(0xFF222327)
    val Slate = Color(0xFF2B2C31)
    val DeepBlue = Color(0xFF176BBD)
    val Brass = Color(0xFFE1A33A)
    val Coral = Color(0xFFD7675D)
    val Sky = Color(0xFFD3A9B5)
    val RoonBlue = Color(0xFF2F9CFF)
    val Paper = Color(0xFFF1F1F3)
    val Mist = Color(0xFFEEF0F6)
    val Smoke = Color(0xFFA8A8AE)
}

private val EchoDarkScheme = darkColorScheme(
    primary = EchoColors.Sky,
    onPrimary = Color(0xFF251B20),
    secondary = EchoColors.Sky,
    onSecondary = Color(0xFF251B20),
    tertiary = EchoColors.Coral,
    background = EchoColors.Night,
    onBackground = EchoColors.Paper,
    surface = EchoColors.Ink,
    onSurface = EchoColors.Paper,
    surfaceVariant = EchoColors.Slate,
    onSurfaceVariant = Color(0xFFBCBBC2),
    outline = Color(0xFF5D5C63),
    outlineVariant = Color(0xFF3A3A40),
)

private val EchoLightScheme = lightColorScheme(
    primary = EchoColors.DeepBlue,
    onPrimary = Color(0xFFFFFFFF),
    secondary = EchoColors.RoonBlue,
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
val LocalEchoDarkTheme = staticCompositionLocalOf { true }
val LocalEchoEffectivePerformanceMode = staticCompositionLocalOf { EchoEffectivePerformanceMode.Balanced }

fun echoFontFamilyForMode(
    mode: String,
    importedFontFamily: FontFamily? = null,
): FontFamily = when (mode) {
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
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    playbackHapticsEnabled: Boolean = true,
    fontFamily: FontFamily = FontFamily.SansSerif,
    fontScale: Float = 1f,
    densityScale: Float = 1f,
    effectivePerformanceMode: EchoEffectivePerformanceMode = EchoEffectivePerformanceMode.Balanced,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val widthClass = rememberEchoWidthSizeClass()
    val colorScheme = remember(darkTheme, dynamicColor, context) {
        val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= 31
        when {
            useDynamic && darkTheme -> dynamicDarkColorScheme(context)
            useDynamic -> dynamicLightColorScheme(context)
            darkTheme -> EchoDarkScheme
            else -> EchoLightScheme
        }
    }
    CompositionLocalProvider(
        LocalEchoDensityScale provides densityScale.coerceIn(0.90f, 1.12f),
        LocalEchoDarkTheme provides darkTheme,
        LocalEchoEffectivePerformanceMode provides effectivePerformanceMode,
        LocalEchoWidthSizeClass provides widthClass,
        LocalEchoContentMaxWidth provides widthClass.contentMaxWidth(),
        LocalEchoHapticsEnabled provides playbackHapticsEnabled,
    ) {
        val typography = remember(fontFamily, fontScale) {
            echoTypography(fontFamily, fontScale.coerceIn(0.88f, 1.18f))
        }
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}
