package app.echo.android.design

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

// All weights share the existing variable font resource; no duplicate font binaries.
@OptIn(ExperimentalTextApi::class)
internal val EchoOutfitFontFamily = FontFamily(
    (100..900 step 100).map { weight ->
        Font(
            resId = R.font.outfit_variable,
            weight = FontWeight(weight),
            loadingStrategy = FontLoadingStrategy.Async,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    },
)
