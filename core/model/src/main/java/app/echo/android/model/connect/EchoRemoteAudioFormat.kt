package app.echo.android.model.connect

data class EchoRemoteAudioFormat(
    val codec: String? = null,
    val mimeType: String? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    val channelCount: Int? = null,
    val lossless: Boolean? = null,
) {
    fun merge(other: EchoRemoteAudioFormat?): EchoRemoteAudioFormat {
        if (other == null) return this
        return EchoRemoteAudioFormat(
            codec = other.codec?.takeIf { it.isNotBlank() } ?: codec,
            mimeType = other.mimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
                ?: mimeType,
            sampleRateHz = other.sampleRateHz?.takeIf { it > 0 } ?: sampleRateHz,
            bitDepth = other.bitDepth?.takeIf { it > 0 } ?: bitDepth,
            channelCount = other.channelCount?.takeIf { it > 0 } ?: channelCount,
            lossless = other.lossless ?: lossless,
        )
    }
}
