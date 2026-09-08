package app.echo.android.model.settings

import kotlin.math.abs

/** Shared background settings patch, inspired by the desktop wallpaper presets. */
enum class EchoBackgroundStyle(
    val id: String,
    val blur: Float,
    val brightness: Float,
    val glass: Float,
    val scale: Float,
) {
    Natural("natural", 0f, 0.92f, 0.28f, 1f),
    Soft("soft", 8f, 0.84f, 0.42f, 1.06f),
    Airy("airy", 0f, 1.06f, 0.16f, 1f),
    Dreamy("dreamy", 14f, 1.12f, 0.32f, 1.10f),
    Cinematic("cinematic", 4f, 0.72f, 0.24f, 1.08f),
    Focus("focus", 16f, 0.78f, 0.64f, 1.12f),
    ;

    fun matches(
        blur: Float,
        brightness: Float,
        glass: Float,
        scale: Float,
        maxBlur: Float,
        isVideo: Boolean,
    ): Boolean =
        (isVideo || abs(this.blur.coerceAtMost(maxBlur) - blur.coerceAtMost(maxBlur)) < 0.001f) &&
            abs(this.brightness - brightness) < 0.001f &&
            abs(this.glass - glass) < 0.001f &&
            abs(this.scale - scale) < 0.001f
}
