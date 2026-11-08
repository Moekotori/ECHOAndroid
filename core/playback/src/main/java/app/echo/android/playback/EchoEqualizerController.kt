package app.echo.android.playback

import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.EchoEqualizerPreset
import app.echo.android.model.playback.EchoEqualizerPresets
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.EchoMsebState
import app.echo.android.model.playback.EchoEqualizerUserPreset
import app.echo.android.model.playback.EchoEqualizerUserPresets
import app.echo.android.model.playback.EchoParametricEq
import app.echo.android.model.playback.OpraEqBand
import app.echo.android.model.playback.OpraHeadphoneCorrectionPreset
import app.echo.android.model.playback.EchoEqOptions
import app.echo.android.model.playback.EchoEqAudition
import app.echo.android.model.playback.EchoHeadphoneCorrectionSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.ceil
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

@UnstableApi
class EchoEqualizerController {
    val processor = EchoEqualizerAudioProcessor(onProcessingFormatChanged = { rate ->
        val changed = _state.value.processingSampleRateHz != rate
        _state.update { it.copy(processingSampleRateHz = rate) }
        if (changed && rate != null) updateCurveForRate(rate)
    }, onProcessingStateChanged = { state -> _state.update { it.copy(processingState = state) } })
    val headphoneProcessor = EchoEqualizerAudioProcessor(
        onProcessingFormatChanged = { rate -> _state.update { it.copy(correctionSampleRateHz = rate) } },
        onProcessingStateChanged = { state -> _state.update { it.copy(correctionProcessingState = state) } },
    )

    private val _state = MutableStateFlow(
        EchoEqualizerState(supported = true, available = true),
    )
    val state: StateFlow<EchoEqualizerState> = _state.asStateFlow()

    private var desiredEnabled: Boolean = false
    private var desiredPresetId: String = EchoEqualizerPreset.Flat
    private var desiredGainsDb: List<Float> = EchoEqualizerPresets.gainsForPreset(EchoEqualizerPreset.Flat)
    private var desiredPreampDb: Float = 0f
    private var desiredParametric: Boolean = false
    private var desiredFilters: List<OpraEqBand> = emptyList()
    private var desiredSourceLabel: String? = null
    private var desiredMseb = EchoMsebState()
    private var desiredOptions = EchoEqOptions()
    private var desiredAudition = EchoEqAudition()
    private var desiredCorrection = EchoHeadphoneCorrectionSettings()
    private var lastShouldProcess: Boolean = false
    private var curveFilters: List<OpraEqBand>? = null
    private var filterCurve = emptyList<app.echo.android.model.playback.EchoEqResponsePoint>()
    private var channelCurves = emptyMap<app.echo.android.model.playback.EchoEqBandScope, List<app.echo.android.model.playback.EchoEqResponsePoint>>()
    private var curveRate = 0f
    private var rateCurveJob: Job? = null
    private var peakJob: Job? = null
    private var peakFilters: List<OpraEqBand>? = null
    private var peakRate = 0f
    private var combinedCurveFilters: List<OpraEqBand>? = null
    private var combinedBoostDb = 0f

    fun setConfig(
        enabled: Boolean,
        presetId: String,
        gainsDb: List<Float>,
        preampDb: Float = 0f,
        filters: List<OpraEqBand> = emptyList(),
        sourceLabel: String? = null,
        mseb: EchoMsebState = desiredMseb,
        options: EchoEqOptions = desiredOptions,
        correction: EchoHeadphoneCorrectionSettings = desiredCorrection,
    ) {
        desiredOptions = options.normalized
        desiredCorrection = correction
        desiredMseb = mseb.normalized
        desiredEnabled = enabled
        desiredPresetId = EchoEqualizerPresets.normalizePresetId(presetId)
        if (desiredPresetId != EchoEqualizerPreset.Custom) {
            clearParametric()
            desiredGainsDb = EchoEqualizerPresets.gainsForPreset(desiredPresetId)
        } else if (filters.isNotEmpty()) {
            desiredParametric = true
            desiredFilters = filters
            desiredPreampDb = preampDb
            desiredSourceLabel = sourceLabel?.takeIf { it.isNotBlank() }
            desiredGainsDb = gainsDb.ifEmpty {
                EchoEqualizerEngine.visualizationGainsDb(filters)
            }
        } else if (gainsDb.size == 5 && gainsDb.any { kotlin.math.abs(it) >= .05f }) {
            desiredParametric = true
            desiredFilters = EchoEqualizerPresets.legacyFilters(gainsDb)
            desiredGainsDb = EchoEqualizerEngine.visualizationGainsDb(desiredFilters)
        } else {
            clearParametric()
            desiredGainsDb = gainsDb.ifEmpty {
                EchoEqualizerPresets.gainsForPreset(EchoEqualizerPreset.Custom)
            }
        }
        desiredPreampDb = preampDb.coerceIn(-60f, 24f)
        publish()
    }

    fun setPreamp(gainDb: Float) {
        if (!gainDb.isFinite()) return
        desiredPreampDb = gainDb.coerceIn(-60f, 24f)
        publish()
    }

    fun setOptions(options: EchoEqOptions) {
        val next = options.normalized
        if (desiredOptions == next) return
        desiredOptions = next
        publish()
    }

    fun setHeadphoneCorrection(correction: EchoHeadphoneCorrectionSettings) {
        if (!correction.preampDb.isFinite() || correction.preampDb !in -60f..24f || correction.filters.any { it.dynamicMode != app.echo.android.model.playback.EchoEqDynamicMode.Off }) return
        desiredCorrection = correction
        publish()
    }

    fun setAudition(active: Boolean, frequencyHz: Float = 1000f, q: Float = 1f) {
        desiredAudition = EchoEqAudition(active && desiredEnabled, frequencyHz.coerceIn(20f, 20000f), q.coerceIn(.1f, 12f), System.nanoTime())
        publish()
    }

    fun setMseb(state: EchoMsebState) {
        val next = state.normalized
        if (next == desiredMseb) return
        desiredMseb = next
        publish()
    }

    fun setEnabled(enabled: Boolean) {
        desiredEnabled = enabled
        if (!enabled) desiredAudition = EchoEqAudition()
        publish()
    }

    fun setPreset(presetId: String) {
        desiredPresetId = EchoEqualizerPresets.normalizePresetId(presetId)
        clearParametric()
        desiredGainsDb = EchoEqualizerPresets.gainsForPreset(desiredPresetId)
        desiredPreampDb = EchoEqualizerPresets.preampFor(desiredPresetId)
        publish()
    }

    fun setParametricFilters(filters: List<OpraEqBand>, applySuggestedPreamp: Boolean = false) {
        val sanitized = EchoParametricEq.sanitize(filters) ?: return
        if (desiredParametric && desiredFilters == sanitized && !applySuggestedPreamp) return
        desiredParametric = true
        desiredPresetId = EchoEqualizerPreset.Custom
        desiredFilters = sanitized
        desiredGainsDb = EchoEqualizerEngine.visualizationGainsDb(desiredFilters)
        // Live edits keep the user's preamp; preset/OPRA entry points still request headroom.
        publish(applySuggestedPreamp = applySuggestedPreamp)
    }

    fun setBandGain(index: Int, gainDb: Float) {
        if (desiredParametric || !gainDb.isFinite()) return
        val bands = currentBands()
        val safeIndex = index.coerceIn(0, (bands.size - 1).coerceAtLeast(0))
        val band = bands.getOrNull(safeIndex)
        desiredGainsDb = bands.mapIndexed { bandIndex, currentBand ->
            if (bandIndex == safeIndex) {
                gainDb.coerceIn(currentBand.minGainDb, currentBand.maxGainDb)
            } else {
                currentBand.gainDb
            }
        }.ifEmpty { listOf(gainDb) }
        desiredPresetId = EchoEqualizerPreset.Custom
        clearParametric(resetPreamp = false)
        if (band == null && desiredGainsDb.isNotEmpty()) {
            publish()
            return
        }
        publish()
    }

    fun reset() {
        desiredOptions = EchoEqOptions(); desiredAudition = EchoEqAudition()
        desiredGainsDb = EchoEqualizerPresets.gainsForPreset(EchoEqualizerPreset.Flat)
        if (desiredParametric) {
            desiredPresetId = EchoEqualizerPreset.Custom
            desiredFilters = listOf(OpraEqBand("peak_dip", 1000f, 0f, 1f, null))
            desiredPreampDb = 0f
            desiredSourceLabel = null
        } else {
            desiredPresetId = EchoEqualizerPreset.Flat
            clearParametric()
        }
        publish()
    }

    fun applyUserPreset(preset: EchoEqualizerUserPreset): Boolean {
        val normalized = EchoEqualizerUserPresets.normalize(preset) ?: return false
        if (normalized.fromOpra) {
            if (normalized.filters.any { it.dynamicMode != app.echo.android.model.playback.EchoEqDynamicMode.Off }) return false
            setHeadphoneCorrection(EchoHeadphoneCorrectionSettings.fromFavorite(normalized)); return true
        }
        setConfig(
            enabled = true,
            presetId = if (normalized.parametric) EchoEqualizerPreset.Custom else normalized.graphicPresetId,
            gainsDb = normalized.gainsDb,
            preampDb = normalized.preampDb,
            filters = if (normalized.parametric) normalized.filters else emptyList(),
            sourceLabel = normalized.sourceLabel,
            options = normalized.options,
        )
        return true
    }

    fun applyOpraPreset(preset: OpraHeadphoneCorrectionPreset): List<Float> {
        val safe = EchoParametricEq.sanitize(preset.bands) ?: return emptyList()
        if (safe.any { it.dynamicMode != app.echo.android.model.playback.EchoEqDynamicMode.Off }) return emptyList()
        if (!preset.preampDb.isFinite() || preset.preampDb !in -60f..24f) return emptyList()
        desiredCorrection = EchoHeadphoneCorrectionSettings.fromPreset(preset.copy(bands = safe))
        publish()
        return EchoEqualizerEngine.visualizationGainsDb(preset.bands)
    }

    fun release() {
        processor.setRuntime(EchoEqualizerRuntime())
        publish()
    }

    private fun publish(applySuggestedPreamp: Boolean = false) {
        val bands = EchoEqualizerPresets.defaultBands(desiredGainsDb)
        val processingFilters = EchoEqualizerEngine.processingFilters(
            parametric = desiredParametric,
            filters = desiredFilters,
            gainsDb = desiredGainsDb,
        ).map { it.copy(gainDb = desiredOptions.effectiveGain(it)) }
        val sampleRate = _state.value.processingSampleRateHz?.toFloat() ?: 48000f
        if (curveFilters != processingFilters || curveRate != sampleRate) {
            curveFilters = processingFilters
            curveRate = sampleRate
            filterCurve = EchoEqualizerEngine.responseCurve(processingFilters, sampleRateHz = sampleRate)
            channelCurves = EchoEqChannelResponse.curves(processingFilters, 0f, sampleRate)
            if (processingFilters.any { it.scope != app.echo.android.model.playback.EchoEqBandScope.Stereo }) filterCurve = channelCurves.getValue(app.echo.android.model.playback.EchoEqBandScope.Stereo)
        }
        val maxBoost = (filterCurve.maxOfOrNull { it.gainDb }?.coerceAtLeast(0f) ?: 0f) + 6f * processingFilters.count { it.enabled && it.dynamicMode == app.echo.android.model.playback.EchoEqDynamicMode.Up }
        val suggestedPreamp = if (maxBoost < 0.05f) 0f else -(ceil(maxBoost * 10) / 10 + 0.5f).coerceAtMost(24f)
        // Apply headroom in the same runtime update as the new filters. Restored and manually
        // adjusted gains remain explicit user settings; only selecting a preset chooses a new default.
        if (applySuggestedPreamp) desiredPreampDb = suggestedPreamp
        val processingPreampDb = desiredPreampDb
        val runtime = processingRuntime()
        processor.setRuntime(runtime)
        headphoneProcessor.setRuntime(EchoEqualizerRuntime(desiredCorrection.enabled, desiredCorrection.preampDb,
            desiredCorrection.filters, preserveParameters = true))
        val nextState = EchoEqualizerState(
            enabled = desiredEnabled,
            supported = true,
            available = true,
            presetId = desiredPresetId,
            presetName = EchoEqualizerPresets.nameFor(desiredPresetId),
            bands = bands,
            preampDb = processingPreampDb,
            parametric = desiredParametric,
            sourceLabel = desiredSourceLabel,
            filters = if (desiredParametric) desiredFilters else emptyList(),
            warning = null,
            responseCurve = filterCurve.map { it.copy(gainDb = it.gainDb + processingPreampDb) },
            suggestedPreampDb = suggestedPreamp,
            options = desiredOptions, audition = desiredAudition,
            headphoneCorrection = desiredCorrection,
            channelResponseCurves = channelCurves.mapValues { (_, points) -> points.map { it.copy(gainDb = it.gainDb + processingPreampDb) } },
            mseb = desiredMseb,
            msebHeadroomDb = if (desiredMseb.active) runtime.preampDb - (if (desiredEnabled) desiredPreampDb else 0f) else 0f,
        )
        _state.update { nextState.copy(processingSampleRateHz = it.processingSampleRateHz, processingState = it.processingState,
            correctionSampleRateHz = it.correctionSampleRateHz, correctionProcessingState = it.correctionProcessingState) }
        if (peakFilters != processingFilters || peakRate != sampleRate) {
            peakFilters = processingFilters; peakRate = sampleRate
            peakJob?.cancel()
            val snapshot = _state.value
            peakJob = EchoPlaybackProcessRuntime.scope.launch(Dispatchers.Default) {
                delay(80)
                val peak = EchoEqChannelResponse.peakBoost(processingFilters, sampleRate)
                ensureActive()
                val suggested = if (peak < .05f) 0f else -(ceil(peak * 10) / 10 + .2f).coerceAtMost(60f)
                _state.update { state -> if (state.filters == snapshot.filters && state.gainsDb == snapshot.gainsDb && state.options == snapshot.options &&
                    (state.processingSampleRateHz?.toFloat() ?: 48000f) == sampleRate) state.copy(suggestedPreampDb = suggested) else state }
            }
        }
    }

    // EQ and MSEB keep independent settings and share one PCM filter pass.
    internal fun processingRuntime(eqFilters: List<OpraEqBand> = EchoEqualizerEngine.processingFilters(
        desiredParametric, desiredFilters, desiredGainsDb,
    )): EchoEqualizerRuntime {
        val msebFilters = EchoMsebEngine.filters(desiredMseb)
        val eqPreamp = if (desiredEnabled) desiredPreampDb else 0f
        val combined = (if (desiredEnabled) eqFilters.map { it.copy(gainDb = desiredOptions.effectiveGain(it)) } else emptyList()) + msebFilters
        val preamp = if (msebFilters.isEmpty()) eqPreamp else {
            if (combinedCurveFilters != combined) {
                combinedCurveFilters = combined
                combinedBoostDb = EchoEqualizerEngine.responseCurve(combined).maxOfOrNull { it.gainDb }?.coerceAtLeast(0f) ?: 0f
            }
            minOf(eqPreamp, if (combinedBoostDb < 0.05f) 0f else -(ceil(combinedBoostDb * 10f) / 10f + 0.5f))
        }
        return EchoEqualizerRuntime(desiredEnabled || msebFilters.isNotEmpty() || desiredAudition.active, preamp, combined, msebActive = msebFilters.isNotEmpty(), audition = desiredAudition)
    }

    private fun currentBands() = _state.value.bands.ifEmpty { EchoEqualizerPresets.defaultBands(desiredGainsDb) }

    private fun updateCurveForRate(rate: Int) {
        rateCurveJob?.cancel()
        val snapshot = _state.value
        val raw = EchoEqualizerEngine.processingFilters(snapshot.parametric, snapshot.filters, snapshot.gainsDb)
        val effective = raw.map { it.copy(gainDb = snapshot.options.effectiveGain(it)) }
        rateCurveJob = EchoPlaybackProcessRuntime.scope.launch(Dispatchers.Default) {
            val curves = EchoEqChannelResponse.curves(effective, snapshot.preampDb, rate.toFloat())
            _state.update { state -> if (state.processingSampleRateHz == rate && state.filters == snapshot.filters && state.gainsDb == snapshot.gainsDb && state.options == snapshot.options)
                state.copy(responseCurve = curves.getValue(app.echo.android.model.playback.EchoEqBandScope.Stereo), channelResponseCurves = curves) else state }
        }
    }

    private fun clearParametric(resetPreamp: Boolean = true) {
        desiredParametric = false
        desiredFilters = emptyList()
        if (resetPreamp) desiredPreampDb = 0f
        desiredSourceLabel = null
    }
}
