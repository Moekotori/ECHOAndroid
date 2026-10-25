package app.echo.android.model.playback

enum class EchoUsbBufferMode(val id: String, val transferCount: Int) {
    Responsive("responsive", 8), Balanced("balanced", 16), Stable("stable", 32);
    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Balanced
    }
}

enum class EchoUsbVolumeMode(val id: String) {
    Software("software"), Auto("auto"), Hardware("hardware");
    companion object { fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Software }
}

data class EchoUsbVolumeDiagnostics(val requested: String, val effective: String, val checked: Boolean,
    val readbackVerified: Boolean, val hardwareDb: Float?, val handoff: Boolean, val reason: String?, val sessionClosed: Boolean = false)

/** Zero means follow the source; strict playback overrides format and digital volume. */
data class EchoUsbOutputOptions(
    val sampleRateHz: Int = 0,
    val bitDepth: Int = 0,
    val channelCount: Int = 0,
    val volumePercent: Int = 100,
    val bufferMode: EchoUsbBufferMode = EchoUsbBufferMode.Balanced,
    val keepAliveOnPause: Boolean = true,
    /** Explicit DoP opt-in per USB VID:PID; value is the highest DSD bit rate. */
    val dopDeviceLimits: Map<String, Int> = emptyMap(),
    val volumeMode: EchoUsbVolumeMode = EchoUsbVolumeMode.Software,
) {
    val normalized: EchoUsbOutputOptions get() = copy(
        sampleRateHz = sampleRateHz.takeIf { it in SAMPLE_RATES } ?: 0,
        bitDepth = bitDepth.takeIf { it in listOf(16, 24, 32) } ?: 0,
        channelCount = channelCount.takeIf { it in 1..2 } ?: 0,
        volumePercent = volumePercent.coerceIn(0, 100),
        dopDeviceLimits = dopDeviceLimits.entries.filter { (key, rate) ->
            val ids = key.split(':')
            ids.size == 2 && ids.all { it.toIntOrNull() in 0..65535 } && rate in DOP_DSD_RATES
        }.takeLast(32).associate { it.key to it.value },
    )

    fun needsRebuild(previous: EchoUsbOutputOptions, strict: Boolean): Boolean =
        bufferMode != previous.bufferMode || keepAliveOnPause != previous.keepAliveOnPause ||
            (strict && dopDeviceLimits != previous.dopDeviceLimits) ||
            (!strict && (sampleRateHz != previous.sampleRateHz || bitDepth != previous.bitDepth ||
                channelCount != previous.channelCount))

    companion object {
        val DOP_DSD_RATES = listOf(2_822_400, 5_644_800, 11_289_600, 22_579_200)
        fun dopDeviceKey(vendorId: Int, productId: Int) = "$vendorId:$productId"
        val SAMPLE_RATES = listOf(44_100, 48_000, 88_200, 96_000, 176_400, 192_000, 352_800, 384_000)
    }
}
