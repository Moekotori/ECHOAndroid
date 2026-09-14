package app.echo.android.playback

import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.EchoEqualizerPreset
import app.echo.android.model.playback.EchoEqualizerPresets
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.EchoEqualizerUserPreset
import app.echo.android.model.playback.EchoEqualizerUserPresets
import app.echo.android.model.playback.OpraEqBand
import app.echo.android.model.playback.OpraHeadphoneCorrectionPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.ceil

@UnstableApi
class EchoEqualizerController {
    val processor = EchoEqualizerAudioProcessor { rate ->
        _state.update { it.copy(processingSampleRateHz = rate) }
    }

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
    private var lastShouldProcess: Boolean = false
    private var curveFilters: List<OpraEqBand>? = null
    private var filterCurve = emptyList<app.echo.android.model.playback.EchoEqResponsePoint>()

    fun setConfig(
        enabled: Boolean,
        presetId: String,
        gainsDb: List<Float>,
        preampDb: Float = 0f,
        filters: List<OpraEqBand> = emptyList(),
        sourceLabel: String? = null,
    ) {
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
        } else {
            clearParametric()
            desiredGainsDb = gainsDb.ifEmpty {
                EchoEqualizerPresets.gainsForPreset(EchoEqualizerPreset.Custom)
            }
        }
        desiredPreampDb = preampDb.coerceIn(-24f, 12f)
        publish()
    }

    fun setPreamp(gainDb: Float) {
        if (!gainDb.isFinite()) return
        desiredPreampDb = gainDb.coerceIn(-24f, 12f)
        publish()
    }

    fun setEnabled(enabled: Boolean) {
        desiredEnabled = enabled
        publish()
    }

    fun setPreset(presetId: String) {
        desiredPresetId = EchoEqualizerPresets.normalizePresetId(presetId)
        clearParametric()
        desiredGainsDb = EchoEqualizerPresets.gainsForPreset(desiredPresetId)
        publish(applySuggestedPreamp = true)
    }

    fun setParametricFilters(filters: List<OpraEqBand>) {
        val allowed = setOf("peak_dip", "low_shelf", "high_shelf", "low_pass", "high_pass", "band_stop", "band_pass")
        if (filters.size !in 1..12 || filters.any { it.type !in allowed || !it.frequencyHz.isFinite() || !it.gainDb.isFinite() || it.q?.isFinite() == false }) return
        desiredParametric = true
        desiredPresetId = EchoEqualizerPreset.Custom
        desiredSourceLabel = null
        desiredFilters = filters.map { it.copy(frequencyHz = it.frequencyHz.coerceIn(20f, 20000f), gainDb = it.gainDb.coerceIn(-12f, 12f), q = (it.q ?: 0.707f).coerceIn(0.1f, 10f)) }
        desiredGainsDb = EchoEqualizerEngine.visualizationGainsDb(desiredFilters)
        publish(applySuggestedPreamp = true)
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
        desiredPresetId = EchoEqualizerPreset.Flat
        desiredGainsDb = EchoEqualizerPresets.gainsForPreset(EchoEqualizerPreset.Flat)
        clearParametric()
        publish()
    }

    fun applyUserPreset(preset: EchoEqualizerUserPreset): Boolean {
        val normalized = EchoEqualizerUserPresets.normalize(preset) ?: return false
        setConfig(
            enabled = true,
            presetId = if (normalized.parametric) EchoEqualizerPreset.Custom else normalized.graphicPresetId,
            gainsDb = normalized.gainsDb,
            preampDb = normalized.preampDb,
            filters = if (normalized.parametric) normalized.filters else emptyList(),
            sourceLabel = normalized.sourceLabel,
        )
        return true
    }

    fun applyOpraPreset(preset: OpraHeadphoneCorrectionPreset): List<Float> {
        desiredEnabled = true
        desiredPresetId = EchoEqualizerPreset.Custom
        desiredParametric = true
        desiredFilters = preset.bands
        desiredPreampDb = preset.preampDb
        desiredSourceLabel = preset.displayName
        desiredGainsDb = EchoEqualizerEngine.visualizationGainsDb(preset.bands)
        publish()
        return desiredGainsDb
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
        )
        if (curveFilters != processingFilters) {
            curveFilters = processingFilters
            filterCurve = EchoEqualizerEngine.responseCurve(processingFilters)
        }
        val maxBoost = filterCurve.maxOfOrNull { it.gainDb }?.coerceAtLeast(0f) ?: 0f
        val suggestedPreamp = if (maxBoost < 0.05f) 0f else -(ceil(maxBoost * 10) / 10 + 0.5f).coerceAtMost(24f)
        // Apply headroom in the same runtime update as the new filters. Restored and manually
        // adjusted gains remain explicit user settings; only selecting a preset chooses a new default.
        if (applySuggestedPreamp) desiredPreampDb = suggestedPreamp
        val processingPreampDb = desiredPreampDb
        val runtime = EchoEqualizerRuntime(
            enabled = desiredEnabled,
            preampDb = processingPreampDb,
            filters = processingFilters,
        )
        processor.setRuntime(runtime)
        if (runtime.shouldProcess != lastShouldProcess) {
            lastShouldProcess = runtime.shouldProcess
            // Media3 determines the active processor chain during configure. A same-position seek
            // need not reconfigure it, so activation changes must rebuild the sink once.
            if (!EchoPlaybackProcessRuntime.usbBitPerfectEnabled) EchoPlaybackProcessRuntime.reconfigureAudioPipeline(forceSinkReset = true)
        }
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
        )
        _state.update { nextState.copy(processingSampleRateHz = it.processingSampleRateHz) }
    }

    private fun currentBands() = _state.value.bands.ifEmpty { EchoEqualizerPresets.defaultBands(desiredGainsDb) }

    private fun clearParametric(resetPreamp: Boolean = true) {
        desiredParametric = false
        desiredFilters = emptyList()
        if (resetPreamp) desiredPreampDb = 0f
        desiredSourceLabel = null
    }
}
