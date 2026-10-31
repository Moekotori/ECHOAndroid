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
    /** Initial open can precede the asynchronous settings/device binding. Apply its ceiling now. */
    fun gainForDevice(options: EchoUsbOutputOptions, vendor: Int?, product: Int?): Float {
        val key = deviceKey(vendor, product) ?: return options.volumeGain
        if (options.volumeDeviceKey == key) return options.volumeGain
        val saved = options.deviceVolumeSteps[key] ?: return options.volumeGain
        return minOf(options.volumeGain, gain(saved))
    }

    fun mergeDeviceSnapshot(requested: EchoUsbOutputOptions, current: EchoUsbOutputOptions): EchoUsbOutputOptions =
        requested.copy(volumeDeviceKey = current.volumeDeviceKey, deviceVolumeSteps = current.deviceVolumeSteps,
            volumeStep = if (requested.volumeDeviceKey == current.volumeDeviceKey) requested.volumeStep else current.volumeStep,
            volumePercent = if (requested.volumeDeviceKey == current.volumeDeviceKey) requested.volumePercent else current.volumePercent)

    fun validKey(key: String): Boolean = key.split(':').let { parts ->
        parts.size == 2 && parts.all { it.toIntOrNull() in 0..65535 }
    }
}
