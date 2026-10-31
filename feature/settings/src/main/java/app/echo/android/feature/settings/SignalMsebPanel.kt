package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoSlider
import app.echo.android.design.EchoSwitch
import app.echo.android.model.playback.*
import kotlin.math.roundToInt

@Composable
internal fun SignalMsebPanel(equalizer: EchoEqualizerState, status: EchoPlaybackStatus, onChange: (EchoMsebState) -> Unit) {
    val state = equalizer.mseb
    val bypassed = status.diagnostics.usbBitPerfectEnabled
    val scheme = MaterialTheme.colorScheme
    val title = stringResource(R.string.mseb_title)
    val statusText = stringResource(when {
        bypassed -> R.string.signal_bypass_locked
        !state.enabled -> R.string.signal_preset_while_off
        !state.active -> R.string.mseb_neutral
        !status.isPlaying -> R.string.eq_waiting_audio
        equalizer.processingSampleRateHz == null -> R.string.eq_waiting_pipeline
        else -> R.string.eq_processing
    })
    val live = !bypassed && state.active && status.isPlaying && equalizer.processingSampleRateHz != null
    val presetLabels = listOf(
        stringResource(R.string.mseb_neutral), stringResource(R.string.mseb_warm),
        stringResource(R.string.mseb_vocal), stringResource(R.string.mseb_bass), stringResource(R.string.mseb_clear),
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SignalProcessorHeader(title, statusText, live, state.enabled, !bypassed,
            { onChange(state.copy(enabled = it)) }, warning = bypassed)
        SignalNote(stringResource(if (bypassed) R.string.signal_bypass_detail else R.string.mseb_detail))
        DspChoices(presetLabels, EchoMsebPreset.entries.indexOfFirst { it.values == state.normalized.values }, !bypassed) {
            onChange(EchoMsebPreset.entries[it].applyTo(state))
        }
        if (state.active && !bypassed) {
            SignalReadout(stringResource(R.string.mseb_headroom), formatEqGain(equalizer.msebHeadroomDb))
        }
        val groups = listOf(
            R.string.signal_mseb_tone to listOf(EchoMsebParameter.Temperature, EchoMsebParameter.BassExtension,
                EchoMsebParameter.BassTexture, EchoMsebParameter.NoteThickness),
            R.string.signal_mseb_voice to listOf(EchoMsebParameter.Voice, EchoMsebParameter.FemaleOvertones,
                EchoMsebParameter.SibilanceLf, EchoMsebParameter.SibilanceHf),
            R.string.signal_mseb_space to listOf(EchoMsebParameter.ImpulseResponse, EchoMsebParameter.Air),
        )
        groups.forEach { (heading, parameters) ->
            SignalSection(stringResource(heading)) {
                parameters.forEach { parameter ->
                    key(parameter) {
                        val labels = msebLabels(parameter)
                        MsebControl(stringResource(labels.first), stringResource(labels.second), stringResource(labels.third),
                            state.value(parameter), !bypassed) { onChange(state.withValue(parameter, it)) }
                    }
                }
            }
        }
        TextButton(enabled = !bypassed, onClick = { onChange(EchoMsebPreset.Neutral.applyTo(state)) },
            contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(stringResource(R.string.mseb_reset))
        }
    }
}

@Composable
private fun MsebControl(label: String, low: String, high: String, value: Float, enabled: Boolean, onCommit: (Float) -> Unit) {
    var draft by remember(value, enabled) { mutableFloatStateOf(value) }
    val commit = rememberUpdatedState(onCommit)
    val scheme = MaterialTheme.colorScheme
    val display = if (draft > 0) "+${draft.roundToInt()}" else draft.roundToInt().toString()
    Column {
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            SignalNumericValue(label, draft, -100f..100f, enabled, {
                draft = it.roundToInt().toFloat()
                commit.value(draft)
            }, valueLabel = display, unit = "", decimals = 0)
        }
        EchoSlider(draft, { draft = it.roundToInt().toFloat() },
            enabled = enabled, valueRange = -100f..100f, neutralValue = 0f,
            onValueChangeFinished = { if (enabled) commit.value(draft) },
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "$label: $low / $high"
                stateDescription = display
            })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(low, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text("0", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            Text(high, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}

private fun msebLabels(parameter: EchoMsebParameter): Triple<Int, Int, Int> = when (parameter) {
    EchoMsebParameter.Temperature -> Triple(R.string.mseb_temperature, R.string.mseb_cool, R.string.mseb_warm_dark)
    EchoMsebParameter.BassExtension -> Triple(R.string.mseb_bass_extension, R.string.mseb_light, R.string.mseb_deep)
    EchoMsebParameter.BassTexture -> Triple(R.string.mseb_bass_texture, R.string.mseb_tight, R.string.mseb_thumpy)
    EchoMsebParameter.NoteThickness -> Triple(R.string.mseb_note_thickness, R.string.mseb_crisp, R.string.mseb_thick)
    EchoMsebParameter.Voice -> Triple(R.string.mseb_voice, R.string.mseb_recessed, R.string.mseb_forward)
    EchoMsebParameter.FemaleOvertones -> Triple(R.string.mseb_female_overtones, R.string.mseb_smooth, R.string.mseb_vivid)
    EchoMsebParameter.SibilanceLf -> Triple(R.string.mseb_sibilance_lf, R.string.mseb_soft, R.string.mseb_crisp)
    EchoMsebParameter.SibilanceHf -> Triple(R.string.mseb_sibilance_hf, R.string.mseb_soft, R.string.mseb_crisp)
    EchoMsebParameter.ImpulseResponse -> Triple(R.string.mseb_impulse_response, R.string.mseb_musical, R.string.mseb_fast)
    EchoMsebParameter.Air -> Triple(R.string.mseb_air, R.string.mseb_soft, R.string.mseb_open)
}
