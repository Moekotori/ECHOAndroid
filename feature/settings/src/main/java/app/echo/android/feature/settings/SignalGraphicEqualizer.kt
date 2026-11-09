package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoEqualizerBand
import app.echo.android.model.playback.EchoEqualizerPresets
import app.echo.android.model.playback.EchoEqualizerState

private val ToneLabels = listOf(
    R.string.eq_tone_sub_bass, R.string.eq_tone_warmth, R.string.eq_tone_mid,
    R.string.eq_tone_presence, R.string.eq_tone_air,
)
internal val ToneBandIndices = listOf(63, 250, 1000, 3150, 10000)
    .map(EchoEqualizerPresets.defaultFrequenciesHz::indexOf)

@Composable
internal fun SignalGraphicEqualizer(
    state: EchoEqualizerState,
    fine: Boolean,
    selectedIndex: Int,
    onSelectBand: (Int) -> Unit,
    enabled: Boolean,
    live: Boolean,
    edits: SignalEditSession<List<Float>>,
) {
    val bands = state.bands
    val selected = bands.getOrNull(selectedIndex) ?: return
    val plottedBands = if (fine) bands else ToneBandIndices.map(bands::get)
    val gains = state.gainsDb
    SignalEqPlot(
        points = state.responseCurve,
        markerFrequenciesHz = plottedBands.map { it.frequencyHz },
        markerGainsDb = plottedBands.map { it.gainDb },
        selectedMarkerIndex = if (fine) selectedIndex else -1,
        onMarkerSelected = onSelectBand.takeIf { fine },
        onMarkerDrag = if (fine && enabled) { index, _, gain ->
            val draft = edits.currentOr(gains)
            if (draft[index] != gain) edits.preview(draft.withGain(index, gain))
        } else null,
        onMarkerDragFinished = edits::finish,
        minGainDb = selected.minGainDb,
        maxGainDb = selected.maxGainDb,
        selectByFrequency = fine,
        live = live,
        modifier = Modifier.fillMaxWidth().height(160.dp),
    )
    if (fine) {
        val listState = rememberLazyListState()
        LaunchedEffect(selectedIndex) {
            val layout = listState.layoutInfo
            if (layout.visibleItemsInfo.none {
                    it.index == selectedIndex && it.offset >= layout.viewportStartOffset &&
                        it.offset + it.size <= layout.viewportEndOffset
                }) {
                listState.scrollToItem((selectedIndex - 1).coerceAtLeast(0))
            }
        }
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(bands, key = { it.index }) { band ->
                FilterChip(
                    selected = band.index == selectedIndex,
                    onClick = { onSelectBand(band.index) },
                    label = { Text(eqBandFrequency(band)) },
                )
            }
        }
        key(selectedIndex) {
            GraphicGainSlider(
                label = eqBandFrequency(selected),
                band = selected,
                gains = gains,
                enabled = enabled,
                edits = edits,
                updateGain = { baseline, value -> baseline.withGain(selectedIndex, value) },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-0.5f, 0f, 0.5f).forEach { step ->
                val next = if (step == 0f) 0f else snapEqGain(selected.gainDb + step)
                    .coerceIn(selected.minGainDb, selected.maxGainDb)
                val label = if (step == 0f) stringResource(R.string.eq_zero_band) else formatEqGain(step)
                val action = if (step == 0f) label else stringResource(R.string.eq_step_band, label)
                OutlinedButton(
                    onClick = {
                        val draft = edits.currentOr(gains)
                        val target = if (step == 0f) 0f else snapEqGain(draft[selectedIndex] + step)
                            .coerceIn(selected.minGainDb, selected.maxGainDb)
                        edits.commit(draft.withGain(selectedIndex, target))
                    },
                    enabled = enabled && next != selected.gainDb,
                    modifier = Modifier.weight(1f).semantics { contentDescription = action },
                ) { Text(label) }
            }
        }
    } else {
        SignalNote(stringResource(R.string.eq_tone_hint))
        plottedBands.forEachIndexed { slot, band ->
            key(band.index) {
                GraphicGainSlider(
                    label = stringResource(R.string.eq_tone_label, stringResource(ToneLabels[slot]), eqBandFrequency(band)),
                    band = band,
                    gains = gains,
                    enabled = enabled,
                    edits = edits,
                    updateGain = { baseline, value -> adjustToneBand(bands, baseline, slot, value) },
                )
            }
        }
    }
}

@Composable
private fun GraphicGainSlider(
    label: String,
    band: EchoEqualizerBand,
    gains: List<Float>,
    enabled: Boolean,
    edits: SignalEditSession<List<Float>>,
    updateGain: (List<Float>, Float) -> List<Float>,
) {
    // Keep the original shape during a gesture so reversing direction restores clipped neighbors.
    var baseline by remember { mutableStateOf<List<Float>?>(null) }
    DspValueSlider(
        label = label,
        value = band.gainDb,
        valueRange = band.minGainDb..band.maxGainDb,
        enabled = enabled,
        format = ::formatEqGain,
        onCommit = { value ->
            val initial = baseline ?: edits.currentOr(gains)
            baseline = null
            val next = updateGain(initial, value)
            if (next == edits.currentOr(gains)) edits.finish() else edits.commit(next)
        },
        onPreview = { value ->
            val initial = baseline ?: edits.currentOr(gains).also { baseline = it }
            val next = updateGain(initial, value)
            if (next != edits.currentOr(gains)) edits.preview(next)
        },
        showBounds = false,
    )
}

internal fun adjustToneBand(
    bands: List<EchoEqualizerBand>,
    gains: List<Float>,
    slot: Int,
    targetDb: Float,
): List<Float> {
    val center = ToneBandIndices[slot]
    val lower = ToneBandIndices.getOrNull(slot - 1) ?: -1
    val upper = ToneBandIndices.getOrNull(slot + 1) ?: bands.size
    val delta = targetDb - gains[center]
    // The graphic bands are already log spaced. Taper to zero at adjacent controls.
    return gains.mapIndexed { index, gain ->
        val band = bands[index]
        val weight = when {
            index <= lower || index >= upper -> 0f
            index < center && lower >= 0 -> (index - lower).toFloat() / (center - lower)
            index > center && upper < bands.size -> (upper - index).toFloat() / (upper - center)
            else -> 1f
        }
        (gain + delta * weight).coerceIn(band.minGainDb, band.maxGainDb)
    }
}

private fun eqBandFrequency(band: EchoEqualizerBand): String =
    if (band.preciseFrequencyHz == 31.5f) "31.5 Hz" else formatEqFrequency(band.frequencyHz)

private fun List<Float>.withGain(index: Int, gain: Float): List<Float> =
    toMutableList().apply { this[index] = gain }
