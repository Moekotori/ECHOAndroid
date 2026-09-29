package app.echo.android.feature.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

internal object ExpressivePlayerStyle {
    val PixelPaper = Color(0xFFCCE5AA)
    val PixelInk = Color(0xFF163F23)
    val PosterPaper = Color(0xFFF0FF3D)
    val PosterInk = Color(0xFF11120E)
    val PixelDisplay = FontFamily(Font(R.font.pixel_display, FontWeight.Bold))
    val PixelBody = FontFamily(Font(R.font.pixel_mono))
    val PosterDisplay = FontFamily(Font(R.font.poster_anton))
}

internal const val DefaultPlayerStyle = "classic"

internal fun normalizedPlayerStyle(style: String): String = when (style) {
    "classic", "record_sleeve", "pixel_handheld", "type_poster" -> style
    else -> DefaultPlayerStyle
}
