package app.echo.android.design

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontWeight

// Static instances preserve the actual outlines for each weight. Changing a variable
// font's default-axis metadata alone leaves its Thin outlines in place.
internal val EchoOutfitFontFamily = FontFamily(
    listOf(
        R.font.outfit_400 to FontWeight.Normal,
        R.font.outfit_500 to FontWeight.Medium,
        R.font.outfit_600 to FontWeight.SemiBold,
        R.font.outfit_700 to FontWeight.Bold,
        R.font.outfit_800 to FontWeight.ExtraBold,
        R.font.outfit_900 to FontWeight.Black,
    ).map { (resource, weight) ->
        Font(
            resId = resource,
            weight = weight,
            loadingStrategy = FontLoadingStrategy.Blocking,
        )
    },
)
