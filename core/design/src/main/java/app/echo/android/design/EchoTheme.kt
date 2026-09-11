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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import app.echo.android.model.settings.EchoColorTheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import app.echo.android.model.settings.EchoEffectivePerformanceMode

object EchoColors {
    // Graphite surfaces with dusty-rose controls. Keep accents warm so glass doesn't pick up leftover blue.
    val Night = Color(0xFF19191D)
    val Ink = Color(0xFF222327)
    val Slate = Color(0xFF2B2C31)
    val Rose = Color(0xFF9B5B6A)
    val Brass = Color(0xFFE1A33A)
    val Coral = Color(0xFFD7675D)
    val Sky = Color(0xFFD3A9B5)
    val Paper = Color(0xFFF3F1F2)
    val Mist = Color(0xFFF1EEEF)
    val Smoke = Color(0xFFA8A8AE)
}

private val EchoDarkScheme = darkColorScheme(
    primary = EchoColors.Sky,
    onPrimary = Color(0xFF251B20),
    primaryContainer = Color(0xFF3A2C32),
    onPrimaryContainer = Color(0xFFE4C4CC),
    inversePrimary = Color(0xFF925568),
    secondary = EchoColors.Sky,
    onSecondary = Color(0xFF251B20),
    secondaryContainer = Color(0xFF322A2E),
    onSecondaryContainer = Color(0xFFE4C4CC),
    tertiary = EchoColors.Coral,
    onTertiary = Color(0xFF2A1614),
    tertiaryContainer = Color(0xFF3A2A28),
    onTertiaryContainer = Color(0xFFF0D0C8),
    background = EchoColors.Night,
    onBackground = EchoColors.Paper,
    surface = EchoColors.Ink,
    onSurface = EchoColors.Paper,
    surfaceVariant = EchoColors.Slate,
    onSurfaceVariant = Color(0xFFBCBBC2),
    surfaceTint = EchoColors.Sky,
    inverseSurface = Color(0xFFE8E4E6),
    inverseOnSurface = Color(0xFF1C1C20),
    outline = Color(0xFF5D5C63),
    outlineVariant = Color(0xFF3A3A40),
    surfaceDim = Color(0xFF151519),
    surfaceBright = Color(0xFF2B2C31),
    surfaceContainerLowest = Color(0xFF101014),
    surfaceContainerLow = Color(0xFF1C1C20),
    surfaceContainer = Color(0xFF202126),
    surfaceContainerHigh = Color(0xFF2A2B30),
    surfaceContainerHighest = Color(0xFF323338),
)

private val EchoLightScheme = lightColorScheme(
    primary = Color(0xFF925568),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF5DDE4),
    onPrimaryContainer = Color(0xFF442532),
    secondary = Color(0xFF76616A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0E3E8),
    onSecondaryContainer = Color(0xFF382D33),
    tertiary = Color(0xFF846536),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF4E5CA),
    onTertiaryContainer = Color(0xFF423016),
    background = Color(0xFFF8F5F3),
    onBackground = Color(0xFF29252A),
    surface = Color(0xFFFFFBFA),
    onSurface = Color(0xFF29252A),
    surfaceVariant = Color(0xFFF0E9E7),
    onSurfaceVariant = Color(0xFF6E6268),
    surfaceDim = Color(0xFFE8DFDC),
    surfaceBright = Color(0xFFFFFBFA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCF8F6),
    surfaceContainer = Color(0xFFF5EFEC),
    surfaceContainerHigh = Color(0xFFEFE7E4),
    surfaceContainerHighest = Color(0xFFE9E0DD),
    inverseSurface = Color(0xFF322D31),
    inverseOnSurface = Color(0xFFF8F0F2),
    inversePrimary = Color(0xFFE6B4C5),
    outline = Color(0xFF93848A),
    outlineVariant = Color(0xFFDED2D5),
    surfaceTint = Color(0xFF925568),
)

val LocalEchoDensityScale = staticCompositionLocalOf { 1f }
val LocalEchoDarkTheme = staticCompositionLocalOf { true }
val LocalEchoTheme = staticCompositionLocalOf { echoThemeTokens(EchoColorTheme.Echo, dark = true) }
val LocalEchoEffectivePerformanceMode = staticCompositionLocalOf { EchoEffectivePerformanceMode.Balanced }

@Composable
fun echoTheme(): EchoThemeTokens = LocalEchoTheme.current

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
    colorTheme: EchoColorTheme = EchoColorTheme.Default,
    playbackHapticsEnabled: Boolean = true,
    fontFamily: FontFamily = FontFamily.SansSerif,
    fontScale: Float = 1f,
    densityScale: Float = 1f,
    effectivePerformanceMode: EchoEffectivePerformanceMode = EchoEffectivePerformanceMode.Balanced,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val widthClass = rememberEchoWidthSizeClass()
    val tokens = remember(colorTheme, darkTheme) { echoThemeTokens(colorTheme, darkTheme) }
    val colorScheme = remember(tokens, darkTheme, dynamicColor, context) {
        val base = echoColorScheme(tokens)
        val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= 31
        if (!useDynamic) {
            base
        } else {
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            base.copy(
                primary = dynamic.primary,
                onPrimary = dynamic.onPrimary,
                primaryContainer = dynamic.primaryContainer,
                onPrimaryContainer = dynamic.onPrimaryContainer,
                secondary = dynamic.secondary,
                onSecondary = dynamic.onSecondary,
                tertiary = dynamic.tertiary,
                onTertiary = dynamic.onTertiary,
                inversePrimary = dynamic.inversePrimary,
                surfaceTint = dynamic.primary,
            )
        }
    }
    CompositionLocalProvider(
        LocalEchoDensityScale provides densityScale.coerceIn(0.90f, 1.12f),
        LocalEchoDarkTheme provides darkTheme,
        LocalEchoTheme provides tokens,
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

internal fun echoColorScheme(tokens: EchoThemeTokens) =
    if (tokens.id == EchoColorTheme.Echo.id) {
        if (tokens.dark) EchoDarkScheme else EchoLightScheme
    } else {
        tokens.toMaterialScheme()
    }

private fun EchoThemeTokens.toMaterialScheme() =
    if (dark) {
        val primaryBox = lerp(panel, accent, 0.22f)
        val secondaryBox = lerp(panel, secondary, 0.20f)
        val tertiaryBox = lerp(panel, accentDeep, 0.20f)
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = primaryBox,
            onPrimaryContainer = heading,
            inversePrimary = accentDeep,
            secondary = this.secondary,
            onSecondary = onAccent,
            secondaryContainer = secondaryBox,
            onSecondaryContainer = heading,
            tertiary = accentDeep,
            onTertiary = onAccent,
            tertiaryContainer = tertiaryBox,
            onTertiaryContainer = heading,
            background = night,
            onBackground = onSurface,
            surface = this.surface,
            onSurface = onSurface,
            surfaceVariant = panel,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = accent,
            inverseSurface = onSurface,
            inverseOnSurface = night,
            outline = this.outline,
            outlineVariant = outlineVariant,
            surfaceDim = bgBottom,
            surfaceBright = panel,
            surfaceContainerLowest = night,
            surfaceContainerLow = ink,
            surfaceContainer = panel,
            surfaceContainerHigh = lerp(panel, accent, 0.08f),
            surfaceContainerHighest = lerp(panel, heading, 0.10f),
        )
    } else {
        val primaryBox = lerp(mist, accent, 0.16f)
        val secondaryBox = lerp(mist, secondary, 0.12f)
        val tertiaryBox = lerp(mist, accentDeep, 0.12f)
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = primaryBox,
            onPrimaryContainer = heading,
            inversePrimary = accentDeep,
            secondary = this.secondary,
            onSecondary = onAccent,
            secondaryContainer = secondaryBox,
            onSecondaryContainer = heading,
            tertiary = accentDeep,
            onTertiary = onAccent,
            tertiaryContainer = tertiaryBox,
            onTertiaryContainer = heading,
            background = bgTop,
            onBackground = onSurface,
            surface = this.surface,
            onSurface = onSurface,
            surfaceVariant = mist,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = accent,
            inverseSurface = heading,
            inverseOnSurface = panel,
            outline = this.outline,
            outlineVariant = outlineVariant,
            surfaceDim = lerp(mist, heading, 0.08f),
            surfaceBright = panel,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = panel,
            surfaceContainer = mist,
            surfaceContainerHigh = lerp(mist, accent, 0.08f),
            surfaceContainerHighest = lerp(mist, heading, 0.08f),
        )
    }
