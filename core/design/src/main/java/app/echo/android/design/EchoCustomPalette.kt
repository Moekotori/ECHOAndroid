package app.echo.android.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.echo.android.model.settings.EchoColorTheme
import app.echo.android.model.settings.EchoCustomColors

/**
 * One seed has to read in both modes. Hue and saturation stay with the pick;
 * lightness is mapped into a band that keeps text and controls readable.
 */
internal fun echoCustomThemeTokens(colors: EchoCustomColors, dark: Boolean): EchoThemeTokens {
    val safe = colors.normalized()
    val accentSeed = Color(safe.accent).toEchoHsl()
    val secondarySeed = Color(safe.secondary).toEchoHsl()
    val backgroundSeed = Color(safe.background).toEchoHsl()

    val accent = themedAccent(accentSeed, dark)
    val onAccent = readableOnColor(accent)
    val accentDeep = accentDeepForSwitch(accent)
    val accentText = if (dark) {
        echoColor(accentSeed.hue, accentSeed.saturation.coerceIn(0.04f, 0.32f), 0.90f)
    } else {
        accent
    }
    val secondary = echoColor(
        hue = secondarySeed.hue,
        saturation = secondarySeed.saturation.coerceIn(0.08f, 0.72f),
        lightness = if (dark) mix(0.55f, 0.74f, secondarySeed.lightness) else mix(0.30f, 0.48f, secondarySeed.lightness),
    )
    val background = themedBackground(backgroundSeed, accentSeed.hue, dark)
    val lightenText = dark
    val primaryBox = if (dark) lerp(background.panel, accent, 0.22f) else lerp(background.mist, accent, 0.16f)
    val textBackgrounds = listOf(background.panel, background.bgTop, primaryBox)
    val heading = pushTextContrast(
        color = if (dark) echoColor(backgroundSeed.hue, 0.04f, 0.97f) else echoColor(backgroundSeed.hue, 0.05f, 0.14f),
        backgrounds = textBackgrounds,
        minimum = 4.5f,
        lighten = lightenText,
    )
    val onSurface = pushTextContrast(
        color = if (dark) echoColor(backgroundSeed.hue, 0.05f, 0.90f) else echoColor(backgroundSeed.hue, 0.05f, 0.22f),
        backgrounds = listOf(background.panel, background.bgTop),
        minimum = 4.5f,
        lighten = lightenText,
    )
    val muted = pushTextContrast(
        color = if (dark) echoColor(backgroundSeed.hue, 0.06f, 0.74f) else echoColor(backgroundSeed.hue, 0.05f, 0.42f),
        backgrounds = listOf(background.panel),
        minimum = 3.1f,
        lighten = lightenText,
    )
    return themedPalette(
        id = EchoColorTheme.Custom,
        dark = dark,
        accent = accent,
        accentDeep = accentDeep,
        accentText = accentText,
        onAccent = onAccent,
        secondary = secondary,
        heading = heading,
        muted = muted,
        onSurface = onSurface,
        bgTop = background.bgTop,
        bgMid = background.bgMid,
        bgBottom = background.bgBottom,
        panel = background.panel,
        mist = background.mist,
    )
}

private fun themedAccent(seed: EchoHsl, dark: Boolean): Color {
    val saturation = seed.saturation.coerceIn(0.12f, 0.84f)
    var lightness = if (dark) mix(0.58f, 0.78f, seed.lightness) else mix(0.26f, 0.42f, seed.lightness)
    var accent = echoColor(seed.hue, saturation, lightness)
    var guard = 0
    while (contrastRatio(readableOnColor(accent), accent) < 4.5f && guard++ < 8) {
        lightness = if (dark) (lightness - 0.03f).coerceAtLeast(0.42f) else (lightness - 0.03f).coerceAtLeast(0.20f)
        accent = echoColor(seed.hue, saturation, lightness)
    }
    return accent
}

private fun accentDeepForSwitch(accent: Color): Color {
    val seed = accent.toEchoHsl()
    var lightness = (seed.lightness - 0.16f).coerceIn(0.16f, 0.46f)
    var deep = echoColor(seed.hue, seed.saturation.coerceIn(0.16f, 0.80f), lightness)
    var guard = 0
    while (contrastRatio(Color.White, lerp(deep, Color.Black, 0.22f)) < 4.5f && guard++ < 10) {
        lightness = (lightness - 0.03f).coerceAtLeast(0.10f)
        deep = echoColor(seed.hue, seed.saturation.coerceIn(0.16f, 0.80f), lightness)
    }
    return deep
}

private fun themedBackground(seed: EchoHsl, accentHue: Float, dark: Boolean): CustomBackground {
    val saturation = seed.saturation.coerceIn(0f, if (dark) 0.40f else 0.18f)
    val topLightness = if (dark) mix(0.055f, 0.145f, seed.lightness) else mix(0.945f, 0.985f, seed.lightness)
    val bgTop = echoColor(seed.hue, saturation, topLightness)
    val bgMid = echoColor(
        seed.hue,
        saturation,
        if (dark) topLightness + 0.035f else (topLightness - 0.025f).coerceAtLeast(0.90f),
    )
    val bgBottom = echoColor(
        mixHue(seed.hue, accentHue, 0.18f),
        saturation,
        if (dark) topLightness + 0.02f else (topLightness - 0.04f).coerceAtLeast(0.88f),
    )
    val panel = if (dark) {
        echoColor(seed.hue, saturation * 0.85f, topLightness + 0.075f)
    } else {
        echoColor(seed.hue, saturation * 0.35f, 0.99f)
    }
    val mist = if (dark) {
        echoColor(seed.hue, saturation, topLightness + 0.025f)
    } else {
        echoColor(seed.hue, saturation * 0.6f, (topLightness - 0.015f).coerceAtLeast(0.92f))
    }
    return CustomBackground(bgTop, bgMid, bgBottom, panel, mist)
}

private fun readableOnColor(background: Color): Color =
    if (contrastRatio(Color.White, background) >= contrastRatio(Color.Black, background)) {
        Color.White
    } else {
        Color.Black
    }

private fun pushTextContrast(
    color: Color,
    backgrounds: List<Color>,
    minimum: Float,
    lighten: Boolean,
): Color {
    var current = color
    repeat(14) {
        if (backgrounds.all { background -> contrastRatio(current, background) >= minimum }) return current
        val hsl = current.toEchoHsl()
        val next = if (lighten) (hsl.lightness + 0.025f).coerceAtMost(0.98f) else (hsl.lightness - 0.025f).coerceAtLeast(0.05f)
        if (next == hsl.lightness) return current
        current = echoColor(hsl.hue, hsl.saturation, next)
    }
    return current
}

private fun mix(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction.coerceIn(0f, 1f)

private fun mixHue(from: Float, to: Float, amount: Float): Float {
    val delta = ((to - from + 540f) % 360f) - 180f
    return (from + delta * amount + 360f) % 360f
}

private data class CustomBackground(
    val bgTop: Color,
    val bgMid: Color,
    val bgBottom: Color,
    val panel: Color,
    val mist: Color,
)
