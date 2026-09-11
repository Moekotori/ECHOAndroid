package app.echo.android.feature.settings

import app.echo.android.feature.settings.R as L10nR

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoExpand
import app.echo.android.design.EchoMotion
import app.echo.android.design.EchoTextButton
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoGlassRowBrush
import app.echo.android.design.echoTheme
import app.echo.android.model.playback.EchoEqualizerPreset
import app.echo.android.model.playback.EchoEqualizerPresetDefinition
import app.echo.android.model.playback.EchoEqualizerPresets
import app.echo.android.model.playback.EchoEqualizerState

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
                    if (state.parametric) {
                        state.sourceLabel ?: stringResource(L10nR.string.diag_eq_parametric)
                    } else {
                        eqPresetLabel(state.presetId)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
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
            Switch(checked = state.enabled, onCheckedChange = onEnabledChange, modifier = Modifier.semantics { contentDescription = title })
        }
        state.warning?.let { SignalNote(it, error = true) }

        SignalEqWell(Modifier.fillMaxWidth()) {
            if (state.parametric) {
                SignalEqPlot(
                    points = state.responseCurve,
                    live = live,
                    showFrequencyLabels = true,
                    modifier = Modifier.fillMaxWidth().height(168.dp),
                )
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    modifier = Modifier.fillMaxWidth().height(152.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 12.dp).heightIn(min = 220.dp),
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

        if (!state.parametric) {
            Column(
                Modifier.fillMaxWidth().selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EchoEqualizerPresets.presets.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            EqPresetCard(
                                preset = preset,
                                selected = state.presetId == preset.id,
                                onSelect = { onPresetSelected(preset.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
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
private fun EqPresetCard(
    preset: EchoEqualizerPresetDefinition,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val theme = echoTheme()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val shape = RoundedCornerShape(18.dp)
    val borderColor by animateColorAsState(
        targetValue = if (selected) scheme.primary else if (theme.dark) theme.glassBorder else scheme.outlineVariant,
        animationSpec = if (lightweight) snap() else tween(EchoMotion.FadeMs, easing = EchoMotion.Silk),
        label = "eq-preset-border",
    )
    Column(
        modifier
            .clip(shape)
            .background(echoGlassRowBrush(selected))
            .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (theme.dark) Color.Black.copy(alpha = 0.28f) else scheme.surface.copy(alpha = 0.72f))
                .padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            SignalEqSparkline(
                gainsDb = preset.gainsDb,
                frequenciesHz = EchoEqualizerPresets.defaultFrequenciesHz,
                active = selected,
            )
        }
        Text(
            eqPresetLabel(preset.id),
            color = if (selected) scheme.primary else scheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun eqPresetLabel(id: String): String = stringResource(when (id) {
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
