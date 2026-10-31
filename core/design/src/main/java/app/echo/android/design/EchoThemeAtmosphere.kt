package app.echo.android.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.echo.android.model.settings.EchoColorTheme

/**
 * Presets other than the signature Echo look carry their color into the page itself.
 * Light pages are washed with the accent's hue, dark pages lift their mid and foot tones
 * toward the accent pair, so switching theme changes the whole canvas, not just the controls.
 */
internal fun EchoThemeTokens.withAtmosphere(): EchoThemeTokens =
    when (id) {
        EchoColorTheme.Echo.id, EchoColorTheme.Custom.id -> this
        // Obsidian's dark page is true black for OLED; only its light page is washed.
        EchoColorTheme.Obsidian.id -> if (dark) this else lightAtmosphere()
        else -> if (dark) darkAtmosphere() else lightAtmosphere()
    }

private fun EchoThemeTokens.lightAtmosphere(): EchoThemeTokens {
    val seed = accent.toEchoHsl()
    val pair = secondary.toEchoHsl()
    val hue = seed.hue
    val footHue = mixHue(hue, pair.hue, 0.28f)
    // Near-grey accents (slate, graphite) stay calm; saturated ones get a clearly tinted page.
    val tint = (seed.saturation * 0.85f).coerceIn(0.12f, 0.55f)
    val top = echoColor(hue, tint, 0.945f)
    val mid = echoColor(hue, tint, 0.925f)
    val bottom = echoColor(footHue, tint * 0.95f, 0.905f)
    val panelColor = echoColor(hue, tint * 0.75f, 0.982f)
    val mistColor = echoColor(hue, tint * 0.9f, 0.915f)
    val line = echoColor(hue, tint * 0.7f, 0.84f)
    val mutedColor = readableOn(muted, bottom, minimum = 4.6f)
    return copy(
        muted = mutedColor,
        onSurfaceVariant = mutedColor,
        bgTop = top,
        bgMid = mid,
        bgBottom = bottom,
        night = top,
        ink = panelColor,
        panel = panelColor,
        surface = panelColor,
        mist = mistColor,
        glassWash = panelColor,
        glassBorder = line,
        softLine = line,
        outlineVariant = line,
        outline = lerp(onSurface, panelColor, 0.42f),
    )
}

private fun EchoThemeTokens.darkAtmosphere(): EchoThemeTokens {
    val mid = lerp(bgMid, accent, 0.12f)
    val bottom = lerp(bgBottom, secondary, 0.14f)
    val panelColor = lerp(panel, accent, 0.07f)
    return copy(
        bgMid = mid,
        bgBottom = bottom,
        panel = panelColor,
        mist = lerp(mist, accent, 0.05f),
        surface = mid,
        ink = mid,
        glassWash = lerp(panelColor, secondary, 0.12f),
    )
}

private fun readableOn(color: Color, background: Color, minimum: Float): Color {
    var current = color
    repeat(12) {
        if (contrastRatio(current, background) >= minimum) return current
        current = lerp(current, Color.Black, 0.08f)
    }
    return current
}

private fun mixHue(from: Float, to: Float, amount: Float): Float {
    val delta = ((to - from + 540f) % 360f) - 180f
    return (from + delta * amount + 360f) % 360f
}
