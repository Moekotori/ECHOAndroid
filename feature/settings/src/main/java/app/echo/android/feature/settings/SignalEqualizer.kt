package app.echo.android.feature.settings

import app.echo.android.feature.settings.R as L10nR

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.*
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SignalEqualizer(
    state: EchoEqualizerState,
    onEnabledChange: (Boolean) -> Unit,
    onPresetSelected: (String) -> Unit,
    onBandGainChange: (Int, Float) -> Unit,
    onReset: () -> Unit,
) {
    val title = stringResource(L10nR.string.feature_settings_equalizer_7ccb03)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                SignalNote(equalizerDetail(state))
            }
            Switch(checked = state.enabled, onCheckedChange = onEnabledChange, modifier = Modifier.semantics { contentDescription = title })
        }
        state.warning?.let { SignalNote(it, error = true) }
        if (!state.enabled) SignalNote(stringResource(L10nR.string.feature_settings_choose_a_preset_then_enable_eq_to_hear_76c79b))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EchoEqualizerPresets.presets.forEach { preset ->
                FilterChip(selected = state.presetId == preset.id && !state.parametric,
                    onClick = { onPresetSelected(preset.id) }, label = { Text(preset.name) },
                    shape = RoundedCornerShape(4.dp), border = null)
            }
        }
        // Warn before the sliders: moving a band replaces the parametric correction.
        if (state.parametric) {
            SignalNote(stringResource(R.string.diag_eq_opra_active, state.sourceLabel ?: stringResource(R.string.diag_eq_parametric)))
            SignalNote(stringResource(R.string.diag_eq_opra_override))
        }
        state.bands.forEach { band ->
            val frequency = formatEqFrequency(band.frequencyHz)
            val enabled = state.enabled && state.supported
            val controlColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(frequency, Modifier.width(62.dp), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = band.gainDb.coerceIn(band.minGainDb, band.maxGainDb),
                    onValueChange = { onBandGainChange(band.index, (it * 10f).roundToInt() / 10f) },
                    valueRange = band.minGainDb..band.maxGainDb,
                    enabled = enabled,
                    modifier = Modifier.weight(1f).semantics { contentDescription = frequency },
                    thumb = { Box(Modifier.size(width = 4.dp, height = 20.dp).background(controlColor)) },
                    track = { sliderState ->
                        SliderDefaults.Track(sliderState = sliderState, enabled = enabled,
                            modifier = Modifier.height(4.dp), thumbTrackGapSize = 0.dp, drawStopIndicator = null)
                    },
                )
                Text(formatEqGain(band.gainDb), Modifier.width(56.dp), style = MaterialTheme.typography.labelMedium, color = controlColor,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                SignalNote(if (state.active) stringResource(R.string.diag_eq_active) else stringResource(L10nR.string.feature_settings_eq_is_not_changing_the_signal_da1f47))
            }
            TextButton(onClick = onReset) { Text(stringResource(L10nR.string.feature_settings_reset_1106f5)) }
        }
        if (abs(state.preampDb) >= 0.05f) SignalNote(stringResource(R.string.diag_eq_preamp, formatEqGain(state.preampDb)))
    }
}
