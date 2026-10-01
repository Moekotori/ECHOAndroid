package app.echo.android.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Shared UI scale, also used by font previews. Android's accessibility scale applies separately. */
fun echoTypography(
    fontFamily: FontFamily = FontFamily.Default,
    fontScale: Float = 1f,
): Typography {
    val scale = fontScale.coerceIn(0.88f, 1.18f)
    fun style(size: Int, height: Int, weight: FontWeight) = TextStyle(
        fontFamily = fontFamily,
        fontWeight = weight,
        fontSize = (size * scale).sp,
        lineHeight = (height * scale).sp,
        // Keep mixed CJK / Latin text natural, including captions and controls.
        letterSpacing = 0.sp,
    )
    return Typography(
        displayLarge = style(44, 52, FontWeight.SemiBold),
        displayMedium = style(36, 44, FontWeight.SemiBold),
        displaySmall = style(32, 40, FontWeight.SemiBold),
        headlineLarge = style(28, 36, FontWeight.SemiBold),
        headlineMedium = style(26, 34, FontWeight.SemiBold),
        headlineSmall = style(24, 32, FontWeight.SemiBold),
        titleLarge = style(20, 28, FontWeight.SemiBold),
        titleMedium = style(16, 24, FontWeight.Medium),
        titleSmall = style(14, 20, FontWeight.Medium),
        bodyLarge = style(16, 24, FontWeight.Normal),
        bodyMedium = style(14, 22, FontWeight.Normal),
        bodySmall = style(13, 20, FontWeight.Normal),
        labelLarge = style(14, 20, FontWeight.Medium),
        labelMedium = style(12, 18, FontWeight.Medium),
        labelSmall = style(12, 16, FontWeight.Normal),
    )
}
