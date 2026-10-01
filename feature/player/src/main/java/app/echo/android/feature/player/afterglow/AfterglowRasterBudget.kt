package app.echo.android.feature.player.afterglow

import kotlin.math.sqrt

internal data class AfterglowRasterSize(val width: Int, val height: Int, val dial: Int, val spritePixels: Int) {
    val totalPixels: Int get() = width * height + dial * dial + spritePixels
}

/** The dial and boat share the background's pixel budget instead of silently doubling its cap. */
internal fun afterglowRasterSize(width: Int, height: Int, maxSide: Int, scene: AfterglowScene): AfterglowRasterSize {
    val side = maxSide.coerceIn(240, 720)
    val w = width.coerceAtLeast(1).toDouble()
    val h = height.coerceAtLeast(1).toDouble()
    val spritePixels = when (scene) {
        AfterglowScene.Voyage -> 112 * 84
        AfterglowScene.LakeMist -> 224 * 90
        else -> 0
    }
    val dial = if (scene == AfterglowScene.StarChart) minOf(w, h) * 0.98 else 0.0
    val scale = minOf(1.0, side / maxOf(w, h), sqrt((side.toDouble() * side - spritePixels) / (w * h + dial * dial)))
    return AfterglowRasterSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1),
        (dial * scale).toInt(), spritePixels)
}
