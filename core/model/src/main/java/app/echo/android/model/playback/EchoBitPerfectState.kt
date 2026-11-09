package app.echo.android.model.playback

enum class EchoBitPerfectState {
    Off, Waiting, Direct, UnsupportedSource, UsbUnavailable, UnsupportedFormat,
    ClockUnverified, VolumeChanged, TransportError, PlaybackError, DopConfigurationRequired, Compatible;

    fun locksUsbVolume(bitPerfectPreferred: Boolean): Boolean = bitPerfectPreferred && this != Compatible
}

/** Facts reported by the decoder and USB output. */
data class EchoBitPerfectStatus(
    val state: EchoBitPerfectState = EchoBitPerfectState.Waiting,
    val sourceBits: Int? = null,
    val decodedBits: Int? = null,
    val outputBits: Int? = null,
    val sampleRateHz: Int? = null,
)
