package app.echo.android.feature.settings

import app.echo.android.feature.settings.R as L10nR

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import app.echo.android.design.EchoSwitch
import app.echo.android.model.playback.EchoEqualizerPreset
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.EchoEqualizerUserPresets
import app.echo.android.model.playback.normalizedType
import kotlin.math.roundToInt

@Composable
fun SignalEqualizer(
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
    onImportBundle: suspend (List<app.echo.android.model.playback.EchoEqualizerUserPreset>, app.echo.android.model.playback.EchoEqImportConflict) -> Unit = { _, _ -> },
    outputDeviceLabel: String? = null,
    outputBound: Boolean = false,
    onBindToOutput: () -> Unit = {},
    onUnbindFromOutput: () -> Unit = {},
    onParametricPreview: ((List<app.echo.android.model.playback.OpraEqBand>) -> Unit)? = null,
    onOptionsChange: (app.echo.android.model.playback.EchoEqOptions) -> Unit = {},
    onOptionsPreview: (app.echo.android.model.playback.EchoEqOptions) -> Unit = onOptionsChange,
    onAudition: (Boolean, Float, Float) -> Unit = { _, _, _ -> },
) {
    var optionDraft by remember { mutableStateOf<app.echo.android.model.playback.EchoEqOptions?>(null) }
    val commitOptions = rememberUpdatedState(onOptionsChange)
    DisposableEffect(Unit) { onDispose { optionDraft?.let { commitOptions.value(it) } } }
    val preampRange = minOf(-12f, state.preampDb)..maxOf(6f, state.preampDb)
    var responseScope by rememberSaveable { mutableIntStateOf(0) }
    val displayCurve = state.channelResponseCurves[app.echo.android.model.playback.EchoEqBandScope.entries[responseScope]] ?: state.responseCurve
    val edits = rememberSignalEditSession(onParametricPreview, onParametricChange)
    var confirmGraphic by rememberSaveable { mutableStateOf(false) }
    var selectedBand by rememberSaveable { mutableIntStateOf(0) }
    val activeBand = selectedBand.coerceIn(0, (state.filters.size - 1).coerceAtLeast(0))
    val title = stringResource(L10nR.string.feature_settings_equalizer_7ccb03)
    val scheme = MaterialTheme.colorScheme
    val live = state.active && !bypassed
    val canAdjust = state.supported && !bypassed
    val fadersEnabled = state.enabled && canAdjust
    val status = stringResource(when {
        bypassed -> L10nR.string.signal_bypass_locked
        !state.enabled -> L10nR.string.eq_status_off
        state.processingState == app.echo.android.model.playback.EchoEqProcessingState.Failed -> L10nR.string.eq_pc_native_failed
        !state.active -> L10nR.string.peq_neutral
        !playing -> L10nR.string.eq_waiting_audio
        state.processingState == app.echo.android.model.playback.EchoEqProcessingState.Compatibility -> L10nR.string.eq_pc_compat_processing
        state.processingSampleRateHz == null -> L10nR.string.eq_waiting_pipeline
        else -> L10nR.string.eq_processing
    })
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SignalProcessorHeader(
            label = title, status = status,
            live = live && playing && state.processingSampleRateHz != null,
            checked = state.enabled, enabled = canAdjust,
            onCheckedChange = onEnabledChange, warning = bypassed,
            detail = if (state.parametric) stringResource(L10nR.string.eq_filter_count, state.filters.size) else null,
        )
        DspChoices(listOf(stringResource(L10nR.string.eq_mode_graphic), stringResource(L10nR.string.eq_mode_parametric)),
            if (state.parametric) 1 else 0, canAdjust) { mode ->
            if (mode == 1 && !state.parametric) {
                onParametricChange(state.bands.map {
                    app.echo.android.model.playback.OpraEqBand("peak_dip", it.preciseFrequencyHz, it.gainDb, app.echo.android.model.playback.EchoEqualizerPresets.GraphicQ, null)
                })
            } else if (mode == 0 && state.parametric) confirmGraphic = true
        }
        if (confirmGraphic) {
            AlertDialog(
                onDismissRequest = { confirmGraphic = false },
                title = { Text(stringResource(L10nR.string.eq_switch_graphic_title)) },
                text = { Text(stringResource(L10nR.string.eq_switch_graphic_detail)) },
                confirmButton = { TextButton(enabled = canAdjust, onClick = {
                    edits.finish()
                    confirmGraphic = false
                    onPresetSelected(EchoEqualizerPreset.Flat)
                }) { Text(stringResource(L10nR.string.eq_switch_graphic_action)) } },
                dismissButton = { TextButton(onClick = { confirmGraphic = false }) {
                    Text(stringResource(L10nR.string.error_log_clear_cancel))
                } },
            )
        }
        if (bypassed) SignalNote(stringResource(L10nR.string.signal_bypass_detail))
        if (state.parametric) SignalEditorActions(
            title = stringResource(L10nR.string.eq_mode_parametric),
            detail = stringResource(L10nR.string.peq_editor_detail) + "\n\n" + stringResource(L10nR.string.sound_live_edit_detail),
            canUndo = canAdjust && edits.canUndo(state.filters), onUndo = { edits.undo(state.filters) },
            hint = if (!bypassed && state.enabled) stringResource(L10nR.string.sound_live_edit_hint) else null,
        )
        state.warning?.let { SignalNote(it, error = true) }
        SignalEqPresetPicker(state.presetId, onPresetSelected, enabled = canAdjust)
        SignalEqAdvancedOptions(state.options, canAdjust,
            { optionDraft = null; onOptionsChange(it) }, { optionDraft = it; onOptionsPreview(it) })
        if (state.filters.any { it.scope != app.echo.android.model.playback.EchoEqBandScope.Stereo }) {
            DspChoices(listOf(stringResource(L10nR.string.eq_pc_combined), stringResource(L10nR.string.eq_pc_left), stringResource(L10nR.string.eq_pc_right),
                stringResource(L10nR.string.eq_pc_mid), stringResource(L10nR.string.eq_pc_side)), responseScope, true) { responseScope = it }
        }
        SignalNote(stringResource(L10nR.string.eq_pc_compat_note))
        if (state.parametric) state.sourceLabel?.takeIf { it.isNotBlank() }?.let { SignalNote(it) }
        if (!state.parametric) SignalNote(stringResource(L10nR.string.eq_pc_catalogue, app.echo.android.model.playback.EchoEqualizerPresets.presets.size))

        if (state.parametric) {
            SignalEqPlot(
                points = displayCurve,
                markerFrequenciesHz = state.filters.map { it.frequencyHz.roundToInt() },
                markerGainsDb = state.filters.map { it.gainDb },
                live = live,
                showFrequencyLabels = true,
                lockMarkerFrequency = false,
                minGainDb = minOf(-24f, state.filters.minOfOrNull { it.gainDb } ?: -24f),
                maxGainDb = maxOf(12f, state.filters.maxOfOrNull { it.gainDb } ?: 12f),
                selectedMarkerIndex = activeBand,
                onMarkerSelected = { edits.finish(); selectedBand = it },
                onMarkerDrag = if (canAdjust && state.enabled) {
                    { index, frequencyHz, gainDb ->
                        edits.preview(
                            state.filters.toMutableList().also { bands ->
                                val current = bands.getOrNull(index) ?: return@also
                                val type = current.normalizedType()
                                val hasGain = type == app.echo.android.model.playback.EchoEqFilterType.PeakDip ||
                                    type == app.echo.android.model.playback.EchoEqFilterType.LowShelf ||
                                    type == app.echo.android.model.playback.EchoEqFilterType.HighShelf
                                bands[index] = current.copy(frequencyHz = frequencyHz, gainDb = if (hasGain) gainDb else 0f)
                            }, state.filters,
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth().height(192.dp),
                onMarkerDragFinished = edits::finish,
                markerSupportsGain = state.filters.map {
                    it.normalizedType() in listOf(app.echo.android.model.playback.EchoEqFilterType.PeakDip,
                        app.echo.android.model.playback.EchoEqFilterType.LowShelf, app.echo.android.model.playback.EchoEqFilterType.HighShelf)
                },
            )
            SignalEqAudition(state.filters.getOrNull(activeBand), canAdjust && state.enabled, onAudition)
            SignalPeqEditor(state.filters, canAdjust,
                { edits.commit(it, state.filters) }, activeBand,
                { edits.finish(); selectedBand = it }, { edits.preview(it, state.filters) },
                currentFilters = { edits.currentOr(state.filters) })
        } else {
            SignalEqPlot(
                points = displayCurve,
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
            LazyRow(
                Modifier.fillMaxWidth().heightIn(min = 184.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.bands, key = { it.index }) { band ->
                    SignalEqFader(
                        frequencyHz = band.frequencyHz,
                        gainDb = band.gainDb,
                        minGainDb = band.minGainDb,
                        maxGainDb = band.maxGainDb,
                        enabled = fadersEnabled,
                        onGainChange = { onBandGainChange(band.index, it) },
                        modifier = Modifier.width(48.dp),
                        preciseFrequencyHz = band.preciseFrequencyHz,
                    )
                }
            }
        }

        if (state.mseb.active && !bypassed) {
            SignalNote(stringResource(L10nR.string.eq_mseb_combined_detail, formatEqGain(state.msebHeadroomDb)))
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(color = scheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(L10nR.string.eq_preamp), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                SignalNumericValue(stringResource(L10nR.string.eq_preamp), state.preampDb, preampRange,
                    canAdjust, onPreampChange)
            }
            val preampLabel = stringResource(L10nR.string.eq_preamp)
            SignalGainStrip(
                value = state.preampDb.coerceIn(preampRange),
                valueRange = preampRange,
                enabled = canAdjust,
                contentDescription = preampLabel,
                onValueChange = onPreampChange,
            )
            if (state.preampDb > state.suggestedPreampDb + 0.1f) {
                SignalNote(stringResource(L10nR.string.eq_headroom_warning, formatEqGain(state.suggestedPreampDb)), error = true)
                TextButton(
                    onClick = { onPreampChange(state.suggestedPreampDb) },
                    enabled = canAdjust,
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) {
                    Text(stringResource(L10nR.string.eq_apply_headroom))
                }
            }
        }

        TextButton(onClick = onReset, enabled = canAdjust,
            contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(stringResource(L10nR.string.signal_reset_equalizer))
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
            applyEnabled = canAdjust,
            onSave = onSaveUserPreset,
            onUpdate = onUpdateUserPreset,
            onApply = onApplyUserPreset,
            onRename = onRenameUserPreset,
            onDelete = onDeleteUserPreset,
            onImportShareCode = onImportShareCode,
            onImportBundle = onImportBundle,
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
internal fun eqPresetLabel(id: String): String = when (id) {
    EchoEqualizerPreset.Custom -> stringResource(L10nR.string.eq_preset_custom)
    EchoEqualizerPreset.Flat -> stringResource(L10nR.string.eq_preset_flat)
    else -> app.echo.android.model.playback.EchoEqualizerPresets.nameFor(id)
}
