package app.echo.android.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp

/** Theme changes made by the person (mode, palette, schedule) fade; the first moments after launch do not. */
private const val ThemeFadeMs = 360
private const val ThemeStartupGraceMs = 1500L

/**
 * The scheme to hand to MaterialTheme. A change of [target] glides from what is on screen right now,
 * so a second change mid-fade continues smoothly instead of jumping. Material keeps each colour in
 * its own state, so only the content reading a colour is redrawn while this runs.
 */
@Composable
internal fun rememberAnimatedColorScheme(target: ColorScheme, lightweight: Boolean): ColorScheme {
    val flight = remember { ColorSchemeFlight(target) }
    val progress = remember { Animatable(1f) }
    val enteredAt = remember { System.nanoTime() }
    LaunchedEffect(target) {
        if (flight.to === target) return@LaunchedEffect
        flight.from = lerpColorScheme(flight.from, flight.to, progress.value)
        flight.to = target
        val startup = (System.nanoTime() - enteredAt) / 1_000_000L < ThemeStartupGraceMs
        if (lightweight || startup) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(ThemeFadeMs, easing = EchoMotion.Silk))
        }
    }
    val fraction = progress.value
    // For the one frame before the effect above runs, keep showing what is on screen.
    return lerpColorScheme(flight.from, flight.to, fraction)
}

private class ColorSchemeFlight(var from: ColorScheme) {
    var to: ColorScheme = from
}

internal fun lerpColorScheme(from: ColorScheme, to: ColorScheme, fraction: Float): ColorScheme {
    if (fraction <= 0f) return from
    if (fraction >= 1f) return to
    return to.copy(
        primary = lerp(from.primary, to.primary, fraction),
        onPrimary = lerp(from.onPrimary, to.onPrimary, fraction),
        primaryContainer = lerp(from.primaryContainer, to.primaryContainer, fraction),
        onPrimaryContainer = lerp(from.onPrimaryContainer, to.onPrimaryContainer, fraction),
        inversePrimary = lerp(from.inversePrimary, to.inversePrimary, fraction),
        secondary = lerp(from.secondary, to.secondary, fraction),
        onSecondary = lerp(from.onSecondary, to.onSecondary, fraction),
        secondaryContainer = lerp(from.secondaryContainer, to.secondaryContainer, fraction),
        onSecondaryContainer = lerp(from.onSecondaryContainer, to.onSecondaryContainer, fraction),
        tertiary = lerp(from.tertiary, to.tertiary, fraction),
        onTertiary = lerp(from.onTertiary, to.onTertiary, fraction),
        tertiaryContainer = lerp(from.tertiaryContainer, to.tertiaryContainer, fraction),
        onTertiaryContainer = lerp(from.onTertiaryContainer, to.onTertiaryContainer, fraction),
        background = lerp(from.background, to.background, fraction),
        onBackground = lerp(from.onBackground, to.onBackground, fraction),
        surface = lerp(from.surface, to.surface, fraction),
        onSurface = lerp(from.onSurface, to.onSurface, fraction),
        surfaceVariant = lerp(from.surfaceVariant, to.surfaceVariant, fraction),
        onSurfaceVariant = lerp(from.onSurfaceVariant, to.onSurfaceVariant, fraction),
        surfaceTint = lerp(from.surfaceTint, to.surfaceTint, fraction),
        inverseSurface = lerp(from.inverseSurface, to.inverseSurface, fraction),
        inverseOnSurface = lerp(from.inverseOnSurface, to.inverseOnSurface, fraction),
        outline = lerp(from.outline, to.outline, fraction),
        outlineVariant = lerp(from.outlineVariant, to.outlineVariant, fraction),
        surfaceDim = lerp(from.surfaceDim, to.surfaceDim, fraction),
        surfaceBright = lerp(from.surfaceBright, to.surfaceBright, fraction),
        surfaceContainerLowest = lerp(from.surfaceContainerLowest, to.surfaceContainerLowest, fraction),
        surfaceContainerLow = lerp(from.surfaceContainerLow, to.surfaceContainerLow, fraction),
        surfaceContainer = lerp(from.surfaceContainer, to.surfaceContainer, fraction),
        surfaceContainerHigh = lerp(from.surfaceContainerHigh, to.surfaceContainerHigh, fraction),
        surfaceContainerHighest = lerp(from.surfaceContainerHighest, to.surfaceContainerHighest, fraction),
    )
}
