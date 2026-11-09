package app.echo.android.feature.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.EchoEqFilterType
import app.echo.android.model.playback.EchoEqualizerPreset
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.OpraEqBand

private val SimpleGainRange = -6f..6f
private val SimpleBandLabels = listOf(R.string.eq_simple_bass, R.string.eq_preset_vocal, R.string.eq_simple_treble)
private val SimpleBands = listOf(
    OpraEqBand(EchoEqFilterType.LowShelf, 120f, 0f, 0.707f, null),
    OpraEqBand(EchoEqFilterType.PeakDip, 2000f, 0f, 0.707f, null),
    OpraEqBand(EchoEqFilterType.HighShelf, 6000f, 0f, 0.707f, null),
)
private val NeutralPeq = listOf(OpraEqBand(EchoEqFilterType.PeakDip, 1000f, 0f, 1f, null))

@Composable
internal fun SignalSimpleEqualizer(
    state: EchoEqualizerState,
    canAdjust: Boolean,
    onPreview: ((List<OpraEqBand>) -> Unit)?,
    onCommit: (List<OpraEqBand>) -> Unit,
    onReset: () -> Unit,
) {
    val edits = rememberSignalEditSession(onPreview, onCommit)
    val filters = state.filters
    val simpleBands = when {
        !state.parametric && state.presetId == EchoEqualizerPreset.Flat && state.options.tiltDb == 0f -> SimpleBands
        state.parametric && filters == NeutralPeq -> SimpleBands
        state.parametric && filters.size == SimpleBands.size && filters.indices.all { index ->
            filters[index].gainDb in SimpleGainRange && filters[index].copy(gainDb = 0f) == SimpleBands[index]
        } -> filters
        else -> null
    }
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    if (simpleBands == null) {
        val current = state.sourceLabel?.takeIf { it.isNotBlank() }
            ?: if (state.parametric) stringResource(R.string.eq_user_preset_parametric) else eqPresetLabel(state.presetId)
        SignalNote(stringResource(R.string.eq_simple_current_curve, current))
        TextButton(enabled = canAdjust, onClick = { confirmReplace = true }) {
            Text(stringResource(R.string.eq_simple_use_three_bands))
        }
    } else {
        val enabled = state.enabled && canAdjust
        simpleBands.forEachIndexed { index, band ->
            DspValueSlider(
                label = stringResource(SimpleBandLabels[index]),
                value = band.gainDb,
                valueRange = SimpleGainRange,
                enabled = enabled,
                format = ::formatEqGain,
                onCommit = { gain ->
                    val draft = edits.currentOr(simpleBands)
                    if (draft[index].gainDb == gain) edits.finish()
                    else edits.commit(draft.withGain(index, gain), filters)
                },
                onPreview = { gain ->
                    val draft = edits.currentOr(simpleBands)
                    if (draft[index].gainDb != gain) edits.preview(draft.withGain(index, gain), filters)
                },
                showBounds = false,
            )
        }
        TextButton(enabled = canAdjust, onClick = { edits.finish(); onReset() }) {
            Text(stringResource(R.string.signal_reset_equalizer))
        }
    }
    if (confirmReplace && simpleBands == null) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text(stringResource(R.string.eq_simple_use_three_bands)) },
            text = { Text(stringResource(R.string.eq_simple_replace_detail)) },
            confirmButton = {
                TextButton(enabled = canAdjust, onClick = {
                    confirmReplace = false
                    edits.commit(SimpleBands, filters)
                }) { Text(stringResource(R.string.eq_simple_replace)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReplace = false }) {
                    Text(stringResource(R.string.error_log_clear_cancel))
                }
            },
        )
    }
}

private fun List<OpraEqBand>.withGain(index: Int, gain: Float): List<OpraEqBand> =
    toMutableList().apply { this[index] = this[index].copy(gainDb = gain) }
