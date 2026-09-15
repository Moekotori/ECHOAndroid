package app.echo.android.feature.settings

import app.echo.android.feature.settings.R as L10nR

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import app.echo.android.design.EchoSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoExpand
import app.echo.android.design.EchoTextButton
import app.echo.android.model.playback.EchoEqualizerPreset
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.EchoEqualizerUserPresets

@Composable
internal fun SignalEqualizer(
    state: EchoEqualizerState,
    bypassed: Boolean,
    playing: Boolean,
    userPresets: List<app.echo.android.model.playback.EchoEqualizerUserPreset>,
    activeUserPresetId: String?,
    onPreampChange: (Float) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onPresetSelected: (String) -> Unit,
    onBandGainChange: (Int, Float) -> Unit,
    onReset: () -> Unit,
    onParametricChange: (List<app.echo.android.model.playback.OpraEqBand>) -> Unit,
    onSaveUserPreset: (String) -> Unit,
    onApplyUserPreset: (String) -> Unit,
    onRenameUserPreset: (String, String) -> Unit,
    onDeleteUserPreset: (String) -> Unit,
    onImportShareCode: (String) -> Unit,
    outputDeviceLabel: String? = null,
    outputBound: Boolean = false,
    onBindToOutput: () -> Unit = {},
) {
    var showEditor by remember { mutableStateOf(false) }
    var showFilters by remember(state.filters) { mutableStateOf(false) }
    val title = stringResource(L10nR.string.feature_settings_equalizer_7ccb03)
    val scheme = MaterialTheme.colorScheme
    val live = state.enabled && !bypassed
    val fadersEnabled = state.enabled && state.supported
    val status = stringResource(when {
        bypassed -> L10nR.string.eq_bypassed
        !state.enabled -> L10nR.string.eq_disabled
        !playing -> L10nR.string.eq_waiting_audio
        state.processingSampleRateHz == null -> L10nR.string.eq_waiting_pipeline
        else -> L10nR.string.eq_processing
    })
    val subtitle = stringResource(
        when {
            state.parametric -> L10nR.string.eq_parametric_mode
            state.presetId == EchoEqualizerPreset.Harman -> L10nR.string.eq_preset_harman_detail
            else -> L10nR.string.eq_graphic_mode
        },
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SignalLiveDot(active = live && playing && state.processingSampleRateHz != null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (state.parametric) state.sourceLabel ?: subtitle else subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        bypassed -> scheme.error
                        live && playing -> scheme.primary
                        else -> scheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            EchoSwitch(checked = state.enabled, onCheckedChange = onEnabledChange, modifier = Modifier.semantics { contentDescription = title })
        }
        TextButton(onClick = {
            if (!state.parametric) onParametricChange(state.bands.map { app.echo.android.model.playback.OpraEqBand("peak_dip", it.frequencyHz.toFloat(), it.gainDb, 1f, null) })
            showEditor = !showEditor
        }, enabled = !bypassed) { Text(stringResource(L10nR.string.dsp_peq)) }
        state.warning?.let { SignalNote(it, error = true) }

        if (!state.parametric) {
            SignalEqPresetPicker(state.presetId, onPresetSelected)
        }

        SignalEqWell(Modifier.fillMaxWidth()) {
            if (state.parametric) {
                SignalEqPlot(
                    points = state.responseCurve,
                    live = live,
                    showFrequencyLabels = true,
                    modifier = Modifier.fillMaxWidth().height(if (showEditor) 128.dp else 168.dp),
                )
                if (!showEditor) Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SignalNote(stringResource(L10nR.string.eq_parametric_kept, state.filters.size))
                    TextButton(onClick = { showFilters = !showFilters }) {
                        Text(stringResource(if (showFilters) L10nR.string.eq_hide_filters else L10nR.string.eq_show_filters))
                    }
                    EchoExpand(showFilters) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.filters.forEach { band ->
                                SignalReadout(
                                    "${formatEqFrequency(band.frequencyHz.toInt())} · ${band.type}",
                                    "${formatEqGain(band.gainDb)} · ${band.q?.let { "Q $it" } ?: "${band.slope ?: 12f} dB/oct"}",
                                )
                            }
                        }
                    }
                    TextButton(onClick = { onPresetSelected(EchoEqualizerPreset.Flat) }) {
                        Text(stringResource(L10nR.string.eq_use_graphic))
                    }
                }
            } else {
                SignalEqPlot(
                    points = state.responseCurve,
                    markerFrequenciesHz = state.bands.map { it.frequencyHz },
                    live = live,
                    showFrequencyLabels = false,
                    modifier = Modifier.fillMaxWidth().height(112.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 12.dp).heightIn(min = 184.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    state.bands.forEach { band ->
                        SignalEqFader(
                            frequencyHz = band.frequencyHz,
                            gainDb = band.gainDb,
                            minGainDb = band.minGainDb,
                            maxGainDb = band.maxGainDb,
                            enabled = fadersEnabled,
                            onGainChange = { onBandGainChange(band.index, it) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        EchoExpand(showEditor && state.parametric) {
            SignalPeqEditor(state.filters, !bypassed, onParametricChange)
        }


        SignalEqWell(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(L10nR.string.eq_preamp), style = MaterialTheme.typography.titleSmall)
                        Text(formatEqGain(state.preampDb), style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                    }
                    EchoTextButton(text = stringResource(L10nR.string.feature_settings_reset_1106f5), onClick = onReset)
                }
                val preampLabel = stringResource(L10nR.string.eq_preamp)
                SignalGainStrip(
                    value = state.preampDb.coerceIn(-24f, 12f),
                    valueRange = -24f..12f,
                    enabled = true,
                    contentDescription = preampLabel,
                    onValueChange = onPreampChange,
                )
                if (state.preampDb > state.suggestedPreampDb + 0.1f) {
                    SignalNote(stringResource(L10nR.string.eq_headroom_warning, formatEqGain(state.suggestedPreampDb)), error = true)
                    TextButton(onClick = { onPreampChange(state.suggestedPreampDb) }) {
                        Text(stringResource(L10nR.string.eq_apply_headroom))
                    }
                }
            }
        }

        val defaultSaveName = state.sourceLabel?.takeIf { it.isNotBlank() }
            ?: if (state.parametric) stringResource(L10nR.string.eq_user_preset_parametric) else eqPresetLabel(state.presetId)
        val currentShare = remember(state, defaultSaveName) {
            EchoEqualizerUserPresets.capture(
                id = "current",
                name = defaultSaveName,
                state = state,
                updatedAtEpochMs = 0L,
            )
        }
        SignalEqWell(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                SignalEqUserPresets(
                    presets = userPresets,
                    activeId = activeUserPresetId,
                    defaultSaveName = defaultSaveName,
                    currentShare = currentShare,
                    currentCurve = state.responseCurve,
                    enabled = true,
                    onSave = onSaveUserPreset,
                    onApply = onApplyUserPreset,
                    onRename = onRenameUserPreset,
                    onDelete = onDeleteUserPreset,
                    onImportShareCode = onImportShareCode,
                    outputDeviceLabel = outputDeviceLabel,
                    outputBound = outputBound,
                    onBindToOutput = onBindToOutput,
                )
            }
        }
    }
}

@Composable
internal fun SignalLiveDot(active: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (active) scheme.primary else scheme.outlineVariant),
    )
}

@Composable
internal fun eqPresetLabel(id: String): String = stringResource(when (id) {
    EchoEqualizerPreset.Harman -> L10nR.string.eq_preset_harman
    EchoEqualizerPreset.Warm -> L10nR.string.eq_preset_warm
    EchoEqualizerPreset.Bass -> L10nR.string.eq_preset_bass
    EchoEqualizerPreset.Vocal -> L10nR.string.eq_preset_vocal
    EchoEqualizerPreset.Bright -> L10nR.string.eq_preset_bright
    EchoEqualizerPreset.Acoustic -> L10nR.string.eq_preset_acoustic
    EchoEqualizerPreset.Electronic -> L10nR.string.eq_preset_electronic
    EchoEqualizerPreset.Night -> L10nR.string.eq_preset_night
    EchoEqualizerPreset.Custom -> L10nR.string.eq_preset_custom
    else -> L10nR.string.eq_preset_flat
})
