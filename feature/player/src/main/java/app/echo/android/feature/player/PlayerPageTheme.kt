package app.echo.android.feature.player

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import app.echo.android.design.EchoThemeTokens
import app.echo.android.design.echoThemeTokens
import app.echo.android.feature.player.afterglow.AfterglowPalette
import app.echo.android.model.settings.EchoColorTheme
import app.echo.android.model.settings.EchoPlayerPageStyle

internal val LocalPlayerPageStyle = staticCompositionLocalOf { EchoPlayerPageStyle.Classic }

internal val EchoPlayerPageStyle.displayFont: FontFamily?
    get() = when (this) {
        EchoPlayerPageStyle.RecordSleeve -> RecordSleeveStyle.TitleFont
        EchoPlayerPageStyle.PixelHandheld -> ExpressivePlayerStyle.PixelDisplay
        EchoPlayerPageStyle.TypePoster -> ExpressivePlayerStyle.PosterDisplay
        EchoPlayerPageStyle.AfterglowMist -> FontFamily.Serif
        else -> null
    }

internal val EchoPlayerPageStyle.bodyFont: FontFamily?
    get() = when (this) {
        EchoPlayerPageStyle.RecordSleeve -> RecordSleeveStyle.BodyFont
        EchoPlayerPageStyle.PixelHandheld -> ExpressivePlayerStyle.PixelBody
        else -> null
    }

internal fun playerPageTokens(style: EchoPlayerPageStyle): EchoThemeTokens {
    val base = echoThemeTokens(EchoColorTheme.Echo, !style.isLight)
    if (style == EchoPlayerPageStyle.Classic) return base
    val afterglow = AfterglowPalette.forStyle(style.lyricsPreset, 0)
    val background = when (style) {
        EchoPlayerPageStyle.RecordSleeve -> RecordSleeveStyle.Paper
        EchoPlayerPageStyle.PixelHandheld -> ExpressivePlayerStyle.PixelPaper
        EchoPlayerPageStyle.TypePoster -> ExpressivePlayerStyle.PosterPaper
        else -> Color(afterglow.background)
    }
    val ink = when (style) {
        EchoPlayerPageStyle.RecordSleeve -> RecordSleeveStyle.Ink
        EchoPlayerPageStyle.PixelHandheld -> ExpressivePlayerStyle.PixelInk
        EchoPlayerPageStyle.TypePoster -> ExpressivePlayerStyle.PosterInk
        else -> Color(afterglow.foreground)
    }
    val accent = when (style) {
        EchoPlayerPageStyle.RecordSleeve -> RecordSleeveStyle.Wine
        EchoPlayerPageStyle.PixelHandheld, EchoPlayerPageStyle.TypePoster -> ink
        else -> Color(afterglow.accent)
    }
    val muted = ink.copy(alpha = 0.62f)
    return base.copy(accent = accent, accentDeep = accent, accentText = accent,
        onAccent = background, secondary = accent, heading = ink, muted = muted,
        onSurface = ink, onSurfaceVariant = muted, bgTop = background, bgMid = background,
        bgBottom = background, surface = background, panel = background, ink = ink,
        outline = ink.copy(alpha = 0.3f), outlineVariant = ink.copy(alpha = 0.16f))
}
