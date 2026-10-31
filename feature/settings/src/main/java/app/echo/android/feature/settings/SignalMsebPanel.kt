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
import app.echo.android.design.EchoIcon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import app.echo.android.model.playback.*
import kotlin.math.roundToInt

@Composable
internal fun SignalMsebPanel(equalizer: EchoEqualizerState, status: EchoPlaybackStatus, onChange: (EchoMsebState) -> Unit,
    onPreview: ((EchoMsebState) -> Unit)? = null, personal: MsebPresetActions = MsebPresetActions()) {
    val edits = rememberSignalEditSession(onPreview, onChange)
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
            { edits.commit(edits.currentOr(state).copy(enabled = it), state) }, warning = bypassed)
        if (bypassed) SignalNote(stringResource(R.string.signal_bypass_detail))
        SignalEditorActions(
            title = title,
            detail = stringResource(R.string.mseb_detail) + "\n\n" + stringResource(R.string.sound_live_edit_detail),
            canUndo = !bypassed && edits.canUndo(state), onUndo = { edits.undo(state) },
            hint = if (!bypassed && state.enabled) stringResource(R.string.sound_live_edit_hint) else null,
        )
        DspChoices(presetLabels, EchoMsebPreset.entries.indexOfFirst { it.values == state.normalized.values }, !bypassed) {
            edits.commit(EchoMsebPreset.entries[it].applyTo(state), state)
        }
        if (state.active && !bypassed) {
            SignalReadout(stringResource(R.string.mseb_headroom), formatEqGain(equalizer.msebHeadroomDb))
        }
        MsebPersonalPresets(state, personal, !bypassed) { preset ->
            edits.commit(edits.currentOr(state).copy(values = preset.values), state)
        }
        Text(stringResource(R.string.mseb_adjustment_range), style = MaterialTheme.typography.labelLarge)
        DspChoices(listOf("±20", "±40", "±100"), state.adjustmentRange.ordinal, !bypassed) {
            edits.commit(edits.currentOr(state).copy(adjustmentRange = EchoMsebAdjustmentRange.entries[it]), state)
        }
        SignalNote(stringResource(R.string.mseb_adjustment_range_note))
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
                            state.value(parameter), !bypassed, limit = state.adjustmentRange.limit,
                            onPreview = { edits.preview(edits.currentOr(state).withValue(parameter, it), state) },
                            onCommit = { edits.commit(edits.currentOr(state).withValue(parameter, it), state) })
                    }
                }
            }
        }
        TextButton(enabled = !bypassed && state.values.any { it != 0f }, onClick = { edits.commit(EchoMsebPreset.Neutral.applyTo(state), state) },
            contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(stringResource(R.string.mseb_reset))
        }
    }
}

@Composable
private fun MsebControl(label: String, low: String, high: String, value: Float, enabled: Boolean, limit: Float,
    onPreview: (Float) -> Unit, onCommit: (Float) -> Unit) {
    var draft by remember { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value, enabled) {
        if (!enabled) dragging = false
        if (!dragging) draft = value
    }
    val commit = rememberUpdatedState(onCommit)
    val scheme = MaterialTheme.colorScheme
    val display = if (draft > 0) "+${draft.roundToInt()}" else draft.roundToInt().toString()
    Column {
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = { draft = 0f; commit.value(0f) }, enabled = enabled && draft != 0f) {
                EchoIcon(Icons.Outlined.RestartAlt, stringResource(R.string.sound_reset_parameter, label), Modifier.size(18.dp))
            }
            SignalNumericValue(label, draft, -100f..100f, enabled, {
                draft = it.roundToInt().toFloat()
                commit.value(draft)
            }, valueLabel = display, unit = "", decimals = 0)
        }
        EchoSlider(draft.coerceIn(-limit, limit), {
            dragging = true
            draft = it.roundToInt().toFloat().let { next -> if (kotlin.math.abs(next) < 0.5f) 0f else next }
            onPreview(draft)
        },
            enabled = enabled, valueRange = -limit..limit, neutralValue = 0f,
            onValueChangeFinished = { dragging = false; if (enabled) commit.value(draft) },
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
