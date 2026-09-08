package app.echo.android.model.playback

enum class EchoBitPerfectState {
    Off, Waiting, Direct, UnsupportedSource, UsbUnavailable, UnsupportedFormat,
    ClockUnverified, VolumeChanged, TransportError, PlaybackError,
}

/** Facts reported by the strict decoder/output, not inferred from the selected USB route. */
data class EchoBitPerfectStatus(
    val state: EchoBitPerfectState = EchoBitPerfectState.Waiting,
    val sourceBits: Int? = null,
    val decodedBits: Int? = null,
    val outputBits: Int? = null,
    val sampleRateHz: Int? = null,
)
