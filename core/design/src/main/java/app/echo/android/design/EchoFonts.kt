package app.echo.android.design

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * Outfit is a geometric Latin face that reads lighter than system UI fonts,
 * and the bundled variable font's default instance is Thin. Never render
 * below SemiBold; theme styles request heavier weights for CJK fallback.
 */
internal fun outfitRenderedWeight(requested: Int): Int =
    requested.coerceIn(600, 900)

// All weights share the existing variable font resource; no duplicate font binaries.
@OptIn(ExperimentalTextApi::class)
internal val EchoOutfitFontFamily = FontFamily(
    (400..900 step 100).map { weight ->
        Font(
            resId = R.font.outfit_variable,
            weight = FontWeight(weight),
            loadingStrategy = FontLoadingStrategy.Blocking,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(outfitRenderedWeight(weight)),
            ),
        )
    },
)
