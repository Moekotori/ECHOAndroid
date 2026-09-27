package app.echo.android.feature.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.echo.android.design.echoFontFamilyForMode

// Keep Outfit's thin Latin outlines readable while separating body, medium and
// heading weights. The shared mapping collapses all three to at least 700.
@OptIn(ExperimentalTextApi::class)
private val HomeOutfitFontFamily = FontFamily(
    (400..900 step 100).map { weight ->
        Font(
            resId = app.echo.android.design.R.font.outfit_variable,
            weight = FontWeight(weight),
            loadingStrategy = FontLoadingStrategy.Blocking,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight / 2 + 400)),
        )
    },
)

/** Keep the chosen family and font scaling; imported/serif/system fonts remain intact. */
@Composable
internal fun HomeAppearance(content: @Composable () -> Unit) {
    val source = MaterialTheme.typography
    val typography = remember(source) {
        val selectedFamily = source.bodyMedium.fontFamily
        val family = if (selectedFamily == echoFontFamilyForMode("outfit")) HomeOutfitFontFamily else selectedFamily
        source.copy(
            headlineSmall = source.headlineSmall.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
            titleLarge = source.titleLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold,
                fontSize = source.titleLarge.fontSize * (20f / 22f), lineHeight = source.titleLarge.lineHeight * (25f / 28f)),
            titleMedium = source.titleMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium),
            titleSmall = source.titleSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium),
            bodyLarge = source.bodyLarge.copy(fontFamily = family, fontWeight = FontWeight.Normal),
            bodyMedium = source.bodyMedium.copy(fontFamily = family, fontWeight = FontWeight.Normal),
            bodySmall = source.bodySmall.copy(fontFamily = family, fontWeight = FontWeight.Normal),
            labelLarge = source.labelLarge.copy(fontFamily = family, fontWeight = FontWeight.Medium),
            labelMedium = source.labelMedium.copy(fontFamily = family, fontWeight = FontWeight.Normal),
            labelSmall = source.labelSmall.copy(fontFamily = family, fontWeight = FontWeight.Normal),
        )
    }
    MaterialTheme(typography = typography, content = content)
}
