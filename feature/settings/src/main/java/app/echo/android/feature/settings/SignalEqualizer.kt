package app.echo.android.feature.settings

import app.echo.android.feature.settings.R as L10nR

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoSwitch
import app.echo.android.design.EchoTextButton
import app.echo.android.model.playback.EchoEqualizerPreset
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.EchoEqualizerUserPresets
import kotlin.math.roundToInt

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
    onUpdateUserPreset: () -> Unit = {},
    onApplyUserPreset: (String) -> Unit,
    onRenameUserPreset: (String, String) -> Unit,
    onDeleteUserPreset: (String) -> Unit,
    onImportShareCode: (String) -> Unit,
    outputDeviceLabel: String? = null,
    outputBound: Boolean = false,
    onBindToOutput: () -> Unit = {},
    onUnbindFromOutput: () -> Unit = {},
) {
    var modeMenu by remember { mutableStateOf(false) }
    var selectedBand by remember { mutableIntStateOf(0) }
    val activeBand = selectedBand.coerceIn(0, (state.filters.size - 1).coerceAtLeast(0))
    val title = stringResource(L10nR.string.feature_settings_equalizer_7ccb03)
    val scheme = MaterialTheme.colorScheme
    val live = state.enabled && !bypassed
    val fadersEnabled = state.enabled && state.supported
    val status = stringResource(when {
        bypassed -> L10nR.string.eq_bypassed
        !state.enabled -> L10nR.string.eq_status_off
        !playing -> L10nR.string.eq_waiting_audio
        state.processingSampleRateHz == null -> L10nR.string.eq_waiting_pipeline
        else -> L10nR.string.eq_processing
    })
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box {
                    TextButton(onClick = { modeMenu = true }, enabled = !bypassed, contentPadding = PaddingValues(0.dp)) {
                        Text(stringResource(if (state.parametric) L10nR.string.eq_mode_parametric else L10nR.string.eq_mode_graphic), color = scheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        EchoIcon(Icons.Default.KeyboardArrowDown, null, Modifier.size(18.dp).padding(start = 4.dp))
                    }
                    DropdownMenu(modeMenu, { modeMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(L10nR.string.eq_mode_parametric)) }, onClick = {
                            modeMenu = false
                            if (!state.parametric) onParametricChange(state.bands.map { app.echo.android.model.playback.OpraEqBand("peak_dip", it.frequencyHz.toFloat(), it.gainDb, 1f, null) })
                        })
                        DropdownMenuItem(text = { Text(stringResource(L10nR.string.eq_use_graphic)) }, onClick = {
                            modeMenu = false
                            if (state.parametric) onPresetSelected(EchoEqualizerPreset.Flat)
                        })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SignalLiveDot(active = live && playing && state.processingSampleRateHz != null)
                    Text(
                        status,
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            bypassed -> scheme.error
                            live && playing -> scheme.primary
                            else -> scheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (state.parametric) {
                Text(stringResource(L10nR.string.eq_filter_count, state.filters.size), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
            EchoSwitch(checked = state.enabled, onCheckedChange = onEnabledChange, modifier = Modifier.semantics { contentDescription = title })
        }
        state.warning?.let { SignalNote(it, error = true) }
        if (!state.parametric) SignalEqPresetPicker(state.presetId, onPresetSelected)
        if (state.parametric) state.sourceLabel?.takeIf { it.isNotBlank() }?.let { SignalNote(it) }
        if (!state.parametric && state.presetId == EchoEqualizerPreset.Harman) SignalNote(stringResource(L10nR.string.eq_preset_harman_detail))

        if (state.parametric) {
            SignalEqPlot(
                points = state.responseCurve,
                markerFrequenciesHz = state.filters.map { it.frequencyHz.roundToInt() },
                markerGainsDb = state.filters.map { it.gainDb },
                live = live,
                showFrequencyLabels = true,
                lockMarkerFrequency = false,
                minGainDb = app.echo.android.model.playback.EchoParametricEq.MinGainDb,
                maxGainDb = app.echo.android.model.playback.EchoParametricEq.MaxGainDb,
                selectedMarkerIndex = activeBand,
                onMarkerSelected = { selectedBand = it },
                onMarkerDrag = if (!bypassed && state.enabled) {
                    { index, frequencyHz, gainDb ->
                        onParametricChange(
                            state.filters.toMutableList().also { bands ->
                                val current = bands.getOrNull(index) ?: return@also
                                bands[index] = current.copy(frequencyHz = frequencyHz, gainDb = gainDb)
                            },
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth().height(192.dp),
            )
            SignalPeqEditor(state.filters, !bypassed, onParametricChange, activeBand) { selectedBand = it }
        } else {
            SignalEqPlot(
                points = state.responseCurve,
                markerFrequenciesHz = state.bands.map { it.frequencyHz },
                markerGainsDb = state.bands.map { it.gainDb },
                live = live,
                showFrequencyLabels = true,
                lockMarkerFrequency = true,
                minGainDb = state.bands.firstOrNull()?.minGainDb ?: -12f,
                maxGainDb = state.bands.firstOrNull()?.maxGainDb ?: 12f,
                onMarkerDrag = if (fadersEnabled) {
                    { index, _, gainDb -> onBandGainChange(index, gainDb) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth().height(184.dp),
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = 184.dp),
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

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(color = scheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(L10nR.string.eq_preamp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
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
                TextButton(
                    onClick = { onPreampChange(state.suggestedPreampDb) },
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    Text(stringResource(L10nR.string.eq_apply_headroom))
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
        HorizontalDivider(color = scheme.outlineVariant)
        SignalEqUserPresets(
            presets = userPresets,
            activeId = activeUserPresetId,
            defaultSaveName = defaultSaveName,
            currentShare = currentShare,
            currentCurve = state.responseCurve,
            enabled = true,
            onSave = onSaveUserPreset,
            onUpdate = onUpdateUserPreset,
            onApply = onApplyUserPreset,
            onRename = onRenameUserPreset,
            onDelete = onDeleteUserPreset,
            onImportShareCode = onImportShareCode,
            outputDeviceLabel = outputDeviceLabel,
            outputBound = outputBound,
            onBindToOutput = onBindToOutput,
            onUnbindFromOutput = onUnbindFromOutput,
        )
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
