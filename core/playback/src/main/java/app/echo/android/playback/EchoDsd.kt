package app.echo.android.playback

import app.echo.android.model.playback.EchoDsdRates

object EchoDsdMime {
    const val Lsbf = "audio/x-dsd-lsbf"
    const val Msbf = "audio/x-dsd-msbf"
    const val LsbfPlanar = "audio/x-dsd-lsbf-planar"
    const val MsbfPlanar = "audio/x-dsd-msbf-planar"

    @JvmStatic
    fun ffmpegCodecName(mimeType: String?): String? =
        when (mimeType) {
            Lsbf -> "dsd_lsbf"
            Msbf -> "dsd_msbf"
            LsbfPlanar -> "dsd_lsbf_planar"
            MsbfPlanar -> "dsd_msbf_planar"
            else -> null
        }

    @JvmStatic
    fun isDecoderMime(mimeType: String?): Boolean = ffmpegCodecName(mimeType) != null
}

/** FFmpeg dsd2pcm emits DSD÷8 PCM; most AudioTracks cannot open 352.8 kHz. */
object EchoDsdPcm {
    const val Dsd64PcmRateHz = EchoDsdRates.DecoderPcmDsd64Hz
    const val OutputDsd64Hz = EchoDsdRates.OutputDsd64Hz
    const val OutputHighRateHz = EchoDsdRates.OutputHighRateHz

    @JvmStatic
    fun outputSampleRateHz(decoderPcmRateHz: Int): Int =
        EchoDsdRates.outputPcmRateHz(decoderPcmRateHz)

    fun decoderPcmRateHz(dsdRateHz: Int): Int = EchoDsdRates.decoderPcmRateHz(dsdRateHz)

    fun durationUs(dsdSampleCount: Long, dsdRateHz: Int): Long {
        if (dsdSampleCount <= 0L || dsdRateHz <= 0) return 0L
        return dsdSampleCount * 1_000_000L / dsdRateHz
    }
}
