package app.echo.android.model.playback

object EchoDsdRates {
    const val Dsd64Hz = 2_822_400
    const val DecoderPcmDsd64Hz = 352_800
    const val OutputDsd64Hz = 88_200
    const val OutputHighRateHz = 176_400
    const val DopDsd64Hz = 176_400
    const val DopDsd128Hz = 352_800

    fun familyLabel(dsdRateHz: Int): String? {
        if (dsdRateHz < Dsd64Hz || dsdRateHz % Dsd64Hz != 0) return null
        return "DSD${(dsdRateHz / Dsd64Hz) * 64}"
    }

    fun isDsdRate(hz: Int): Boolean = familyLabel(hz) != null

    fun decoderPcmRateHz(dsdRateHz: Int): Int = if (dsdRateHz > 0) dsdRateHz / 8 else dsdRateHz

    fun outputPcmRateHz(rateHz: Int): Int {
        if (rateHz <= 0) return rateHz
        val decoderPcm = if (isDsdRate(rateHz)) decoderPcmRateHz(rateHz) else rateHz
        return if (decoderPcm <= DecoderPcmDsd64Hz) OutputDsd64Hz else OutputHighRateHz
    }

    fun dsdRateFromDecoderPcm(decoderPcmRateHz: Int): Int? {
        if (decoderPcmRateHz < DecoderPcmDsd64Hz) return null
        val dsdRateHz = decoderPcmRateHz * 8
        return dsdRateHz.takeIf { isDsdRate(it) }
    }

    /** 24-bit DoP PCM rate for DSD64/128. Higher rates are not packed in this path. */
    fun dopSampleRateHz(dsdRateHz: Int): Int? {
        if (!isDsdRate(dsdRateHz)) return null
        return when (dsdRateHz / 16) {
            DopDsd64Hz -> DopDsd64Hz
            DopDsd128Hz -> DopDsd128Hz
            else -> null
        }
    }

    fun dopSampleRateFromDecoderPcm(decoderPcmRateHz: Int): Int? =
        dsdRateFromDecoderPcm(decoderPcmRateHz)?.let(::dopSampleRateHz)
}

fun EchoPlaybackDiagnostics.isDsdSource(): Boolean =
    codec.equals("DSD", ignoreCase = true) || (sampleRateHz != null && EchoDsdRates.isDsdRate(sampleRateHz))

fun EchoPlaybackDiagnostics.dsdFamilyLabel(): String? =
    if (isDsdSource()) sampleRateHz?.let(EchoDsdRates::familyLabel) else null

fun EchoPlaybackDiagnostics.isDsdDopOutput(): Boolean {
    val dsdRateHz = sampleRateHz ?: return false
    val decodedRateHz = decodedSampleRateHz ?: return false
    return isDsdSource() && EchoDsdRates.dopSampleRateHz(dsdRateHz) == decodedRateHz
}
