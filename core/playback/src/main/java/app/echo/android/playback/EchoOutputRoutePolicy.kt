package app.echo.android.playback

import android.media.AudioDeviceInfo
import app.echo.android.model.playback.EchoOutputDeviceKind

data class EchoOutputDeviceCandidate(
    val type: Int,
    val name: String? = null,
    val address: String? = null,
    val isSink: Boolean = true,
)

data class EchoOutputRoute(
    val kind: String = EchoOutputDeviceKind.System.id,
    val deviceName: String? = null,
    val bluetoothCodec: String? = null,
    val address: String? = null,
)

object EchoOutputRoutePolicy {
    fun kind(type: Int): EchoOutputDeviceKind = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
        -> EchoOutputDeviceKind.Speaker
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HEARING_AID,
        -> EchoOutputDeviceKind.Wired
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        -> EchoOutputDeviceKind.Bluetooth
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        -> EchoOutputDeviceKind.Usb
        else -> EchoOutputDeviceKind.Other
    }

    fun pickDevice(
        devices: List<EchoOutputDeviceCandidate>,
        preferUsb: Boolean = false,
    ): EchoOutputDeviceCandidate? {
        val sinks = devices.filter { it.isSink }
        if (sinks.isEmpty()) return null
        if (preferUsb) {
            sinks.firstOrNull { kind(it.type) == EchoOutputDeviceKind.Usb }?.let { return it }
        }
        val ranked = sinks.sortedBy { device ->
            when (kind(device.type)) {
                EchoOutputDeviceKind.Bluetooth -> 0
                EchoOutputDeviceKind.Usb -> 1
                EchoOutputDeviceKind.Wired -> 2
                EchoOutputDeviceKind.Speaker -> 4
                EchoOutputDeviceKind.Other -> 3
                EchoOutputDeviceKind.System -> 5
            }
        }
        return ranked.firstOrNull { kind(it.type) != EchoOutputDeviceKind.Speaker }
            ?: ranked.first()
    }

    fun routeLabel(kind: EchoOutputDeviceKind, deviceName: String?, bluetoothCodec: String?): String {
        val name = deviceName?.trim()?.takeIf { it.isNotEmpty() }
        val codec = bluetoothCodec?.trim()?.takeIf { it.isNotEmpty() }
        return buildString {
            append(kind.id)
            if (name != null) {
                append(": ")
                append(name)
            }
            if (codec != null) {
                append(" / ")
                append(codec)
            }
        }
    }

    fun bluetoothCodecName(codecType: Int, codecName: String? = null): String? {
        val mapped = when (codecType) {
            0 -> "SBC"
            1 -> "AAC"
            2 -> "aptX"
            3 -> "aptX HD"
            4 -> "LDAC"
            5 -> "LC3"
            6 -> "Opus"
            else -> null
        }
        if (mapped != null) return mapped
        val raw = codecName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (raw.equals("UNKNOWN", ignoreCase = true) || raw.equals("INVALID", ignoreCase = true)) return null
        return raw
    }
}
