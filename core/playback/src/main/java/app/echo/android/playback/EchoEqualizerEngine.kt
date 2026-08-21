package app.echo.android.playback

import app.echo.android.model.playback.EchoEqFilterType
import app.echo.android.model.playback.EchoEqualizerBand
import app.echo.android.model.playback.EchoEqualizerPresets
import app.echo.android.model.playback.OpraEqBand
import app.echo.android.model.playback.affectsFrequencyResponse
import kotlin.math.abs
import kotlin.math.pow

data class EchoEqualizerRuntime(
    val enabled: Boolean = false,
    val preampDb: Float = 0f,
    val filters: List<OpraEqBand> = emptyList(),
) {
    val preampLinear: Float
        get() = 10.0.pow((preampDb / 20.0)).toFloat()

    val shouldProcess: Boolean
        get() = EchoEqualizerEngine.shouldProcess(enabled, preampDb, filters)
}

object EchoEqualizerEngine {
    const val GraphicBandQ = 1f
    const val GainEpsilonDb = 0.05f

    fun graphicFilters(
        gainsDb: List<Float>,
        frequenciesHz: List<Int> = EchoEqualizerPresets.defaultFrequenciesHz,
    ): List<OpraEqBand> =
        frequenciesHz.mapIndexedNotNull { index, frequencyHz ->
            val gainDb = gainsDb.getOrElse(index) { 0f }
            if (abs(gainDb) < GainEpsilonDb) {
                null
            } else {
                OpraEqBand(
                    type = EchoEqFilterType.PeakDip,
                    frequencyHz = frequencyHz.toFloat(),
                    gainDb = gainDb,
                    q = GraphicBandQ,
                    slope = null,
                )
            }
        }

    fun graphicFilters(bands: List<EchoEqualizerBand>): List<OpraEqBand> =
        graphicFilters(
            gainsDb = bands.map { it.gainDb },
            frequenciesHz = bands.map { it.frequencyHz },
        )

    fun visualizationGainsDb(
        filters: List<OpraEqBand>,
        frequenciesHz: List<Int> = EchoEqualizerPresets.defaultFrequenciesHz,
        minGainDb: Float = -12f,
        maxGainDb: Float = 12f,
        sampleRateHz: Float = DefaultEqSampleRateHz,
    ): List<Float> =
        frequenciesHz.map { frequencyHz ->
            EchoBiquadMath.sampleCurveDb(
                filters = filters,
                frequencyHz = frequencyHz.toFloat(),
                sampleRateHz = sampleRateHz,
                preampDb = 0f,
            ).coerceIn(minGainDb, maxGainDb)
        }

    fun processingFilters(
        parametric: Boolean,
        filters: List<OpraEqBand>,
        gainsDb: List<Float>,
        frequenciesHz: List<Int> = EchoEqualizerPresets.defaultFrequenciesHz,
    ): List<OpraEqBand> =
        if (parametric) {
            filters
        } else {
            graphicFilters(gainsDb, frequenciesHz)
        }

    fun shouldProcess(enabled: Boolean, preampDb: Float, filters: List<OpraEqBand>): Boolean =
        enabled && (abs(preampDb) >= GainEpsilonDb || filters.any { it.affectsFrequencyResponse() })
}
