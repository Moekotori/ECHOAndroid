package app.echo.android.model.playback

/** Only these two controls are device-specific; step, ceiling and mute remain global. */
data class EchoVolumeDeviceLevel(val levelDb: Float = 0f, val gainDb: Float = 0f) {
    val normalized: EchoVolumeDeviceLevel get() = copy(
        levelDb = levelDb.takeIf { it.isFinite() }?.coerceIn(-60f, 0f) ?: 0f,
        gainDb = gainDb.takeIf { it.isFinite() }?.coerceIn(-24f, 12f) ?: 0f,
    )
}

object EchoVolumeDeviceIdentity {
    const val MaxDevices = 32
    const val MaxKeyLength = 200

    fun key(kind: EchoOutputDeviceKind, name: String?, address: String?, usbVendor: Int? = null,
        usbProduct: Int? = null): String? {
        if (kind == EchoOutputDeviceKind.System) return null
        if (kind == EchoOutputDeviceKind.Speaker) return kind.id
        if (kind == EchoOutputDeviceKind.Usb && usbVendor != null && usbProduct != null &&
            usbVendor in 0..65535 && usbProduct in 0..65535) return "usb:$usbVendor:$usbProduct"
        val stableAddress = address?.trim()?.takeIf { it.isNotEmpty() }
        val suffix = stableAddress?.let { "address:${it.lowercase(java.util.Locale.ROOT)}" }
            ?: name?.trim()?.takeIf { it.isNotEmpty() }?.let { "name:$it" }
        return (if (suffix == null) kind.id else "${kind.id}:$suffix").take(MaxKeyLength)
    }

    fun validKey(key: String): Boolean = key.length in 1..MaxKeyLength &&
        key.none { it.isISOControl() } && EchoOutputDeviceKind.entries.any {
            it != EchoOutputDeviceKind.System && (key == it.id || key.startsWith("${it.id}:"))
        }
}
