package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoSlider
import app.echo.android.model.playback.*
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round

/** Keep slider drafts local; rebuild and save filters only at the end of a gesture. */
@Composable
internal fun PeqBandControls(band: OpraEqBand, enabled: Boolean, onCommit: (OpraEqBand) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PeqLogControl(stringResource(R.string.dsp_frequency), band.frequencyHz,
            EchoParametricEq.MinFrequencyHz..EchoParametricEq.MaxFrequencyHz, "Hz", 1, enabled,
            { formatEqFrequency(it.toInt()) }) { onCommit(band.copy(frequencyHz = it)) }
        val type = band.normalizedType()
        if (type == EchoEqFilterType.PeakDip || type == EchoEqFilterType.LowShelf || type == EchoEqFilterType.HighShelf) {
            DspValueSlider(stringResource(R.string.dsp_band_gain), band.gainDb,
                EchoParametricEq.MinGainDb..EchoParametricEq.MaxGainDb, enabled, ::formatEqGain,
                { onCommit(band.copy(gainDb = it)) })
        }
        if (!EchoParametricEq.isPass(type)) {
            PeqLogControl("Q", band.q ?: 0.707f, EchoParametricEq.MinQ..EchoParametricEq.MaxQ,
                "Q", 2, enabled, { signalNumber(it, 2) }) { onCommit(band.copy(q = it)) }
        }
    }
}

@Composable
private fun PeqLogControl(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String,
    decimals: Int, enabled: Boolean, format: (Float) -> String, onCommit: (Float) -> Unit,
) {
    var draft by remember(value, enabled) { mutableFloatStateOf(value.coerceIn(range)) }
    val commit = rememberUpdatedState(onCommit)
    val scheme = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            SignalNumericValue(label, draft, range, enabled, {
                draft = it
                commit.value(it)
            }, valueLabel = format(draft), unit = unit, decimals = decimals)
        }
        EchoSlider(ln(draft), { position ->
            val factor = 10f.pow(decimals)
            draft = (round(exp(position) * factor) / factor).coerceIn(range)
        }, valueRange = ln(range.start)..ln(range.endInclusive), enabled = enabled,
            onValueChangeFinished = { if (enabled) commit.value(draft) },
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = label
                stateDescription = format(draft)
            })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(format(range.start), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text(format(range.endInclusive), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}
