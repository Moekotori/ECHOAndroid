package app.echo.android

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Color

/** Seconds on the splash clock; every element is a pure function of this one timeline. */
internal object LaunchSplashTiming {
    /** Time the splash stays before handing off to the app. */
    const val HoldSeconds = 2.2f
    const val ExitSeconds = 0.75f

    const val LandStart = 0.12f
    const val LandDuration = 1.15f
    const val IdleStart = 1.3f
    const val IdleCycle = 4.2f
    const val LetterStart = 0.95f
    const val LetterStagger = 0.1f
    const val LetterDuration = 0.9f
    const val LoaderStart = 1.6f
    const val EchoStart = 1.9f
    const val EchoCycle = 3.6f
}

internal val LandEasing = CubicBezierEasing(0.3f, 0.7f, 0.3f, 1f)
internal val LetterEasing = CubicBezierEasing(0.16f, 0.84f, 0.3f, 1f)
internal val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
internal val ExitEasing = CubicBezierEasing(0.5f, 0f, 0.2f, 1f)
internal val EchoEasing = CubicBezierEasing(0.2f, 0.6f, 0.3f, 1f)
internal val SweepEasing = CubicBezierEasing(0.6f, 0f, 0.35f, 1f)

internal fun launchSplashProgress(time: Float, start: Float, duration: Float): Float =
    ((time - start) / duration).coerceIn(0f, 1f)

/** Piecewise keyframes with [easing] applied inside each segment, like CSS keyframes. */
internal fun interpolateKeyframes(progress: Float, xs: FloatArray, ys: FloatArray, easing: Easing): Float {
    if (progress <= xs[0]) return ys[0]
    for (i in 1 until xs.size) {
        if (progress <= xs[i]) {
            val fraction = easing.transform((progress - xs[i - 1]) / (xs[i] - xs[i - 1]))
            return ys[i - 1] + (ys[i] - ys[i - 1]) * fraction
        }
    }
    return ys[ys.lastIndex]
}

internal class LaunchSplashPalette(
    val skyTop: Color,
    val skyMid: Color,
    val skyLow: Color,
    val ink: Color,
    val accent: Color,
    val accent2: Color,
    val accent3: Color,
    val ray: Color,
    val blobA: Color,
    val blobB: Color,
    val shadow: Color,
    val track: Color,
    val showAtmosphere: Boolean,
)

private val LightPalette = LaunchSplashPalette(
    skyTop = Color(0xFFD6EEFC), skyMid = Color(0xFFEEF0FB), skyLow = Color(0xFFFFF3EE),
    ink = Color(0xFF22324A), accent = Color(0xFF58B4EC), accent2 = Color(0xFFB79CF0), accent3 = Color(0xFFF5A6CC),
    ray = Color(0x8CFFFFFF), blobA = Color(0xFFBFE4FA), blobB = Color(0xFFF9D3EA),
    shadow = Color(0x3822324A), track = Color(0x1222324A), showAtmosphere = true,
)

private val DarkPalette = LaunchSplashPalette(
    skyTop = Color(0xFF0C1830), skyMid = Color(0xFF121B38), skyLow = Color(0xFF1B1A36),
    ink = Color(0xFFEAF0F8), accent = Color(0xFF7CCBF4), accent2 = Color(0xFFB8A2F4), accent3 = Color(0xFFF7B0D2),
    ray = Color(0x387CCBF4), blobA = Color(0xFF1E4E86), blobB = Color(0xFF5A2C68),
    shadow = Color(0x8C000000), track = Color(0x12EAF0F8), showAtmosphere = true,
)

/** Over a user-picked startup image the pastel wash is dropped and the lettering turns light. */
private val ImagePalette = DarkPalette.let {
    LaunchSplashPalette(
        skyTop = it.skyTop, skyMid = it.skyMid, skyLow = it.skyLow,
        ink = Color.White, accent = it.accent, accent2 = it.accent2, accent3 = it.accent3,
        ray = it.ray, blobA = it.blobA, blobB = it.blobB,
        shadow = it.shadow, track = Color(0x33FFFFFF), showAtmosphere = false,
    )
}

internal fun launchSplashPalette(dark: Boolean, overImage: Boolean): LaunchSplashPalette = when {
    overImage -> ImagePalette
    dark -> DarkPalette
    else -> LightPalette
}
