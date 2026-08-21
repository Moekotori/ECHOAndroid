package app.echo.android.playback

import androidx.media3.common.util.UnstableApi
import app.echo.android.model.playback.EchoEqualizerPreset
import app.echo.android.model.playback.EchoEqualizerPresets
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.OpraEqBand
import app.echo.android.model.playback.OpraHeadphoneCorrectionPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@UnstableApi
class EchoEqualizerController {
    val processor = EchoEqualizerAudioProcessor()

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
        publish()
    }

    fun setBandGain(index: Int, gainDb: Float) {
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
        clearParametric()
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

    private fun publish() {
        val bands = EchoEqualizerPresets.defaultBands(desiredGainsDb)
        val processingFilters = EchoEqualizerEngine.processingFilters(
            parametric = desiredParametric,
            filters = desiredFilters,
            gainsDb = desiredGainsDb,
        )
        val processingPreampDb = if (desiredParametric) desiredPreampDb else 0f
        val runtime = EchoEqualizerRuntime(
            enabled = desiredEnabled,
            preampDb = processingPreampDb,
            filters = processingFilters,
        )
        processor.setRuntime(runtime)
        if (runtime.shouldProcess != lastShouldProcess) {
            lastShouldProcess = runtime.shouldProcess
            EchoPlaybackProcessRuntime.reconfigureAudioPipeline()
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
        )
        if (_state.value != nextState) {
            _state.value = nextState
        }
    }

    private fun currentBands() = _state.value.bands.ifEmpty { EchoEqualizerPresets.defaultBands(desiredGainsDb) }

    private fun clearParametric() {
        desiredParametric = false
        desiredFilters = emptyList()
        desiredPreampDb = 0f
        desiredSourceLabel = null
    }
}
