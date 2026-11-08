package app.echo.android.model.playback

import kotlin.math.abs

data class EchoEqualizerBand(
    val index: Int,
    val frequencyHz: Int,
    val gainDb: Float,
    val minGainDb: Float,
    val maxGainDb: Float,
    val preciseFrequencyHz: Float = frequencyHz.toFloat(),
)

data class EchoEqualizerPresetDefinition(
    val id: String,
    val name: String,
    val gainsDb: List<Float>,
    val preampDb: Float = 0f,
    val sourceLabel: String? = null,
    val sourceUrl: String? = null,
)

data class EchoEqualizerState(
    val enabled: Boolean = false,
    val supported: Boolean = true,
    val available: Boolean = true,
    val presetId: String = EchoEqualizerPreset.Flat,
    val presetName: String = EchoEqualizerPresets.nameFor(EchoEqualizerPreset.Flat),
    val bands: List<EchoEqualizerBand> = EchoEqualizerPresets.defaultBands(),
    val preampDb: Float = 0f,
    val parametric: Boolean = false,
    val sourceLabel: String? = null,
    val filters: List<OpraEqBand> = emptyList(),
    val warning: String? = null,
    val responseCurve: List<EchoEqResponsePoint> = emptyList(),
    val suggestedPreampDb: Float = 0f,
    val processingSampleRateHz: Int? = null,
    val mseb: EchoMsebState = EchoMsebState(),
    val msebHeadroomDb: Float = 0f,
    val options: EchoEqOptions = EchoEqOptions(),
    val audition: EchoEqAudition = EchoEqAudition(),
    val processingState: EchoEqProcessingState = EchoEqProcessingState.Idle,
    val channelResponseCurves: Map<EchoEqBandScope, List<EchoEqResponsePoint>> = emptyMap(),
    val headphoneCorrection: EchoHeadphoneCorrectionSettings = EchoHeadphoneCorrectionSettings(),
    val correctionSampleRateHz: Int? = null,
    val correctionProcessingState: EchoEqProcessingState = EchoEqProcessingState.Idle,
) {
    val active: Boolean
        get() = enabled &&
            supported &&
            (
                abs(preampDb) >= 0.05f ||
                    audition.active ||
                    filters.any { it.enabled && (it.copy(gainDb = options.effectiveGain(it))).affectsFrequencyResponse() } ||
                    (!parametric && bands.any { abs(it.gainDb * options.strengthPercent / 100f) >= 0.1f || abs(options.tiltDb) > 0.01f })
                )

    val gainsDb: List<Float>
        get() = bands.map { it.gainDb }
}

object EchoEqualizerPreset {
    const val Flat = "flat"
    const val Harman = "harman-target"
    const val Warm = "headphone-warm"
    const val Bass = "bass-boost"
    const val Vocal = "vocal-clear"
    const val Bright = "treble-sparkle"
    const val Acoustic = "acoustic-silk"
    const val Electronic = "classic-smiley"
    const val Night = "night"
    const val Custom = "custom"
}

object EchoEqFilterType {
    const val PeakDip = "peak_dip"
    const val LowShelf = "low_shelf"
    const val HighShelf = "high_shelf"
    const val BandStop = "band_stop"
    const val BandPass = "band_pass"
    const val LowPass = "low_pass"
    const val HighPass = "high_pass"

    fun normalize(type: String): String =
        when (type.lowercase().replace('-', '_').replace(' ', '_')) {
            "peak", "peaking", "peak_dip", "peakdip" -> PeakDip
            "low_shelf", "lowshelf", "low_shelve" -> LowShelf
            "high_shelf", "highshelf", "high_shelve" -> HighShelf
            "band_stop", "bandstop", "notch" -> BandStop
            "band_pass", "bandpass" -> BandPass
            "low_pass", "lowpass" -> LowPass
            "high_pass", "highpass" -> HighPass
            else -> type
        }
}

fun OpraEqBand.normalizedType(): String = EchoEqFilterType.normalize(type)

fun OpraEqBand.affectsFrequencyResponse(): Boolean =
    enabled && (dynamicMode != EchoEqDynamicMode.Off || when (normalizedType()) {
        EchoEqFilterType.BandStop,
        EchoEqFilterType.BandPass,
        EchoEqFilterType.LowPass,
        EchoEqFilterType.HighPass,
        -> true
        else -> abs(gainDb) >= 0.05f
    })

object EchoEqualizerPresets {
    const val GraphicQ = 4.318f
    val preciseFrequenciesHz: List<Float> = EchoPcEqPresets.frequencies
    val defaultFrequenciesHz: List<Int> = preciseFrequenciesHz.map { kotlin.math.round(it).toInt() }
    val legacyFrequenciesHz: List<Int> = listOf(60, 230, 910, 3600, 14000)
    val presets: List<EchoEqualizerPresetDefinition> = EchoPcEqPresets.definitions
    private val aliases = mapOf("harman" to "harman-target", "warm" to "headphone-warm", "bass" to "bass-boost",
        "vocal" to "vocal-clear", "bright" to "treble-sparkle", "acoustic" to "acoustic-silk", "electronic" to "classic-smiley")
    fun preampFor(id: String): Float = presets.firstOrNull { it.id == normalizePresetId(id) }?.preampDb ?: 0f
    fun legacyGains(id: String): List<Float>? = legacyPresets.firstOrNull { it.id == id }?.gainsDb
    fun legacyFilters(gains: List<Float>): List<OpraEqBand> = legacyFrequenciesHz.mapIndexed { i, f ->
        OpraEqBand(EchoEqFilterType.PeakDip, f.toFloat(), gains.getOrElse(i) { 0f }, 1f, null)
    }

    private val legacyPresets: List<EchoEqualizerPresetDefinition> = listOf(
        EchoEqualizerPresetDefinition("flat", "Flat", listOf(0f, 0f, 0f, 0f, 0f)),
        // 5-band fit of Harman OE 2018: bass shelf, near-flat mids, pinna presence, air roll-off.
        EchoEqualizerPresetDefinition("harman", "Harman", listOf(5.0f, 1.2f, -0.2f, 1.8f, -1.6f)),
        EchoEqualizerPresetDefinition("warm", "Warm", listOf(2f, 1.2f, 0f, -0.8f, -1.4f)),
        EchoEqualizerPresetDefinition("bass", "Bass", listOf(4f, 2.8f, 0.4f, -0.8f, -1.2f)),
        EchoEqualizerPresetDefinition("vocal", "Vocal", listOf(-1.2f, -0.4f, 2.2f, 1.8f, 0.2f)),
        EchoEqualizerPresetDefinition("bright", "Bright", listOf(-2f, -0.8f, 0f, 2f, 3.2f)),
        EchoEqualizerPresetDefinition("acoustic", "Acoustic", listOf(1.0f, 0.4f, 0.6f, 1.4f, 1.8f)),
        EchoEqualizerPresetDefinition("electronic", "Electronic", listOf(4.8f, 1.0f, -1.4f, 1.2f, 2.6f)),
        EchoEqualizerPresetDefinition("night", "Night", listOf(1.2f, 0.6f, 0f, -1.4f, -2.6f)),
    )

    fun normalizePresetId(id: String?): String {
        if (id == EchoEqualizerPreset.Custom) return EchoEqualizerPreset.Custom
        val key = aliases[id] ?: id
        return presets.firstOrNull { it.id == key }?.id ?: EchoEqualizerPreset.Flat
    }

    fun nameFor(id: String): String =
        if (id == EchoEqualizerPreset.Custom) {
            "Custom"
        } else {
            presets.firstOrNull { it.id == id }?.name ?: presets.first().name
        }

    fun gainsForPreset(
        id: String,
        frequenciesHz: List<Int> = defaultFrequenciesHz,
        customGainsDb: List<Float> = emptyList(),
    ): List<Float> {
        if (id == EchoEqualizerPreset.Custom) {
            return resizedGains(customGainsDb, frequenciesHz.size)
        }
        val preset = presets.firstOrNull { it.id == normalizePresetId(id) } ?: presets.first()
        return frequenciesHz.map { frequencyHz ->
            val sourceIndex = defaultFrequenciesHz.indices.minByOrNull { index ->
                abs(defaultFrequenciesHz[index] - frequencyHz)
            } ?: 0
            preset.gainsDb.getOrElse(sourceIndex) { 0f }
        }
    }

    fun defaultBands(
        gainsDb: List<Float> = gainsForPreset(EchoEqualizerPreset.Flat),
        minGainDb: Float = -24f,
        maxGainDb: Float = 12f,
    ): List<EchoEqualizerBand> {
        val normalized = resizedGains(gainsDb, defaultFrequenciesHz.size)
        return defaultFrequenciesHz.mapIndexed { index, frequencyHz ->
            EchoEqualizerBand(
                index = index,
                frequencyHz = frequencyHz,
                preciseFrequencyHz = preciseFrequenciesHz[index],
                gainDb = normalized.getOrElse(index) { 0f }.coerceIn(minGainDb, maxGainDb),
                minGainDb = minGainDb,
                maxGainDb = maxGainDb,
            )
        }
    }

    private fun resizedGains(gainsDb: List<Float>, size: Int): List<Float> =
        List(size) { index -> gainsDb.getOrElse(index) { 0f } }
}

data class EchoEqResponsePoint(val frequencyHz: Float, val gainDb: Float)
