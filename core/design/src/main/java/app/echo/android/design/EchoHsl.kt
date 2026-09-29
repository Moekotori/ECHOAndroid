package app.echo.android.design

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

data class EchoHsl(
    val hue: Float,
    val saturation: Float,
    val lightness: Float,
)

fun Color.toEchoHsl(): EchoHsl {
    val maxChannel = max(red, max(green, blue))
    val minChannel = min(red, min(green, blue))
    val lightness = (maxChannel + minChannel) / 2f
    val delta = maxChannel - minChannel
    if (delta == 0f) return EchoHsl(0f, 0f, lightness)
    val saturation = if (lightness > 0.5f) delta / (2f - maxChannel - minChannel) else delta / (maxChannel + minChannel)
    val hue = when (maxChannel) {
        red -> ((green - blue) / delta + if (green < blue) 6f else 0f)
        green -> ((blue - red) / delta + 2f)
        else -> ((red - green) / delta + 4f)
    } * 60f
    return EchoHsl(hue, saturation, lightness)
}

fun echoColor(hue: Float, saturation: Float, lightness: Float): Color {
    val safeHue = ((hue % 360f) + 360f) % 360f
    val safeSaturation = saturation.coerceIn(0f, 1f)
    val safeLightness = lightness.coerceIn(0f, 1f)
    if (safeSaturation == 0f) return Color(safeLightness, safeLightness, safeLightness, 1f)
    val q = if (safeLightness < 0.5f) {
        safeLightness * (1f + safeSaturation)
    } else {
        safeLightness + safeSaturation - safeLightness * safeSaturation
    }
    val p = 2f * safeLightness - q
    val hueUnit = safeHue / 360f
    return Color(
        red = hueChannel(p, q, hueUnit + 1f / 3f),
        green = hueChannel(p, q, hueUnit),
        blue = hueChannel(p, q, hueUnit - 1f / 3f),
        alpha = 1f,
    )
}

private fun hueChannel(p: Float, q: Float, raw: Float): Float {
    var t = raw
    if (t < 0f) t += 1f
    if (t > 1f) t -= 1f
    return when {
        t < 1f / 6f -> p + (q - p) * 6f * t
        t < 0.5f -> q
        t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
        else -> p
    }
}
