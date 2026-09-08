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
    bypassed: Boolean,
    playing: Boolean,
    onPreampChange: (Float) -> Unit,
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
        SignalNote(stringResource(when {
            bypassed -> L10nR.string.eq_bypassed
            !state.enabled -> L10nR.string.eq_disabled
            !playing -> L10nR.string.eq_waiting_audio
            state.processingSampleRateHz == null -> L10nR.string.eq_waiting_pipeline
            else -> L10nR.string.eq_processing
        }))
        SignalEqCurve(state.responseCurve)
        state.warning?.let { SignalNote(it, error = true) }
        if (!state.enabled) SignalNote(stringResource(L10nR.string.feature_settings_choose_a_preset_then_enable_eq_to_hear_76c79b))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EchoEqualizerPresets.presets.forEach { preset ->
                FilterChip(selected = state.presetId == preset.id && !state.parametric,
                    onClick = { onPresetSelected(preset.id) }, label = { Text(eqPresetLabel(preset.id)) },
                    shape = RoundedCornerShape(4.dp), border = null)
            }
        }
        // Warn before the sliders: moving a band replaces the parametric correction.
        if (state.parametric) {
            Text(state.sourceLabel ?: stringResource(L10nR.string.diag_eq_parametric), style = MaterialTheme.typography.titleSmall)
            SignalNote(stringResource(L10nR.string.eq_parametric_kept, state.filters.size))
            state.filters.forEach { band ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${formatEqFrequency(band.frequencyHz.toInt())} · ${band.type}", style = MaterialTheme.typography.bodySmall)
                    Text("${formatEqGain(band.gainDb)} · ${band.q?.let { "Q $it" } ?: "${band.slope ?: 12f} dB/oct"}", style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = { onPresetSelected(EchoEqualizerPreset.Flat) }) {
                Text(stringResource(L10nR.string.eq_use_graphic))
            }
        }
        if (!state.parametric) state.bands.forEach { band ->
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

        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(L10nR.string.eq_preamp), style = MaterialTheme.typography.titleSmall)
            Text(formatEqGain(state.preampDb), style = MaterialTheme.typography.labelLarge)
        }
        val preampLabel = stringResource(L10nR.string.eq_preamp)
        Slider(value = state.preampDb.coerceIn(-24f, 12f), onValueChange = { onPreampChange((it * 10).roundToInt() / 10f) },
            valueRange = -24f..12f, modifier = Modifier.fillMaxWidth().semantics { contentDescription = preampLabel })
        if (state.preampDb > state.suggestedPreampDb + 0.1f) {
            SignalNote(stringResource(L10nR.string.eq_headroom_warning, formatEqGain(state.suggestedPreampDb)), error = true)
            TextButton(onClick = { onPreampChange(state.suggestedPreampDb) }) { Text(stringResource(L10nR.string.eq_apply_headroom)) }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                SignalNote(stringResource(if (state.parametric) L10nR.string.eq_parametric_mode else L10nR.string.eq_graphic_mode))
            }
            TextButton(onClick = onReset) { Text(stringResource(L10nR.string.feature_settings_reset_1106f5)) }
        }
    }
}

@Composable
private fun eqPresetLabel(id: String): String = stringResource(when (id) {
    EchoEqualizerPreset.Warm -> L10nR.string.eq_preset_warm
    EchoEqualizerPreset.Bass -> L10nR.string.eq_preset_bass
    EchoEqualizerPreset.Vocal -> L10nR.string.eq_preset_vocal
    EchoEqualizerPreset.Bright -> L10nR.string.eq_preset_bright
    else -> L10nR.string.eq_preset_flat
})
