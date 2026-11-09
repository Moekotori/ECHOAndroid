package app.echo.android.feature.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoEqBandScope
import app.echo.android.model.playback.EchoEqOptions
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.OpraEqBand
import app.echo.android.model.playback.normalizedType
import kotlin.math.roundToInt

@Composable
internal fun SignalParametricEqualizer(
    state: EchoEqualizerState,
    canAdjust: Boolean,
    live: Boolean,
    edits: SignalEditSession<List<OpraEqBand>>,
    onAudition: (Boolean, Float, Float) -> Unit,
) {
    val filters = state.filters
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val activeBand = selected.coerceIn(0, filters.lastIndex.coerceAtLeast(0))
    var scope by rememberSaveable { mutableIntStateOf(0) }
    val channelSpecific = filters.any { it.scope != EchoEqBandScope.Stereo }
    if (channelSpecific) {
        DspChoices(
            listOf(R.string.eq_pc_combined, R.string.eq_pc_left, R.string.eq_pc_right,
                R.string.eq_pc_mid, R.string.eq_pc_side).map { stringResource(it) },
            scope, true,
        ) { scope = it }
    }
    val response = if (channelSpecific) state.channelResponseCurves[EchoEqBandScope.entries[scope]] else null
    val gainEditable = filters.map { it.normalizedType() in EchoEqOptions.GainTypes }
    val selectBand: (Int) -> Unit = { edits.finish(); selected = it }
    SignalEqPlot(
        points = response ?: state.responseCurve,
        markerFrequenciesHz = filters.map { it.frequencyHz.roundToInt() },
        markerGainsDb = filters.map { it.gainDb },
        live = live,
        lockMarkerFrequency = false,
        minGainDb = minOf(-24f, filters.minOfOrNull { it.gainDb } ?: -24f),
        maxGainDb = maxOf(12f, filters.maxOfOrNull { it.gainDb } ?: 12f),
        selectedMarkerIndex = activeBand,
        onMarkerSelected = selectBand,
        onMarkerDrag = if (canAdjust) { index, frequency, gain ->
            val current = edits.currentOr(filters)
            val band = current.getOrNull(index)
            if (band != null) {
                val next = band.copy(frequencyHz = frequency, gainDb = if (gainEditable[index]) gain else 0f)
                if (next != band) {
                    edits.preview(current.toMutableList().apply { this[index] = next }, filters)
                }
            }
        } else null,
        onMarkerDragFinished = edits::finish,
        markerSupportsGain = gainEditable,
        modifier = Modifier.fillMaxWidth().height(168.dp),
    )
    SignalPeqEditor(
        filters = filters,
        enabled = canAdjust,
        onChange = { edits.commit(it, filters) },
        selectedBand = activeBand,
        onSelectBand = selectBand,
        onPreview = { edits.preview(it, filters) },
        currentFilters = { edits.currentOr(filters) },
    )
    SignalEqAudition(filters.getOrNull(activeBand), canAdjust && state.enabled, onAudition)
    SignalEditorActions(
        title = stringResource(R.string.eq_mode_parametric),
        detail = stringResource(R.string.peq_editor_detail) + "\n\n" + stringResource(R.string.sound_live_edit_detail),
        canUndo = canAdjust && edits.canUndo(filters),
        onUndo = { edits.undo(filters) },
        hint = if (canAdjust && state.enabled) stringResource(R.string.sound_live_edit_hint) else null,
    )
}
