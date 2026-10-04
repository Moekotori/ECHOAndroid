package app.echo.android.model.playback

enum class EchoUsbBufferMode(val id: String, val transferCount: Int) {
    Responsive("responsive", 8), Balanced("balanced", 16), Stable("stable", 32);
    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Balanced
    }
}

/** Zero means follow the source; strict playback overrides format and digital volume. */
data class EchoUsbOutputOptions(
    val sampleRateHz: Int = 0,
    val bitDepth: Int = 0,
    val channelCount: Int = 0,
    val volumePercent: Int = 100,
    val bufferMode: EchoUsbBufferMode = EchoUsbBufferMode.Balanced,
    val keepAliveOnPause: Boolean = true,
) {
    val normalized: EchoUsbOutputOptions get() = copy(
        sampleRateHz = sampleRateHz.takeIf { it in SAMPLE_RATES } ?: 0,
        bitDepth = bitDepth.takeIf { it in listOf(16, 24, 32) } ?: 0,
        channelCount = channelCount.takeIf { it in 1..2 } ?: 0,
        volumePercent = volumePercent.coerceIn(0, 100),
    )

    fun needsRebuild(previous: EchoUsbOutputOptions, strict: Boolean): Boolean =
        bufferMode != previous.bufferMode || keepAliveOnPause != previous.keepAliveOnPause ||
            (!strict && (sampleRateHz != previous.sampleRateHz || bitDepth != previous.bitDepth ||
                channelCount != previous.channelCount))

    companion object {
        val SAMPLE_RATES = listOf(44_100, 48_000, 88_200, 96_000, 176_400, 192_000, 352_800, 384_000)
    }
}
