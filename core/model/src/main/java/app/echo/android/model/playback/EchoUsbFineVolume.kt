package app.echo.android.model.playback

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** 0 is mute; 1..300 are -59.8..0 dB in 0.2 dB increments. */
object EchoUsbFineVolume {
    const val MaxStep = 300
    const val MaxDevices = 16
    fun gain(step: Int): Float = if (step <= 0) 0f else
        10.0.pow((step.coerceAtMost(MaxStep) - MaxStep) * 0.2 / 20.0).toFloat()
    fun db(step: Int): Float = (step.coerceIn(0, MaxStep) - MaxStep) / 5f
    fun fromPercent(percent: Int): Int = if (percent <= 0) 0 else
        floor(MaxStep + 20 * log10(percent.coerceAtMost(100) / 100.0) * 5).toInt().coerceIn(1, MaxStep)
    fun percent(step: Int): Int = (gain(step) * 100).roundToInt()
    fun deviceKey(vendor: Int?, product: Int?): String? =
        if (vendor in 0..65535 && product in 0..65535) "$vendor:$product" else null
    fun validKey(key: String): Boolean = key.split(':').let { parts ->
        parts.size == 2 && parts.all { it.toIntOrNull() in 0..65535 }
    }
}
