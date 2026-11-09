package app.echo.android.feature.settings

import app.echo.android.feature.settings.R as L10nR

import androidx.compose.foundation.background
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoEqualizerPreset
import app.echo.android.model.playback.EchoEqualizerPresets
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.EchoEqualizerUserPresets
import app.echo.android.model.playback.EchoEqFilterType
import app.echo.android.model.playback.EchoEqOptions
import app.echo.android.model.playback.OpraEqBand

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
    onBandGainsChange: (List<Float>) -> Unit,
    onBandGainsPreview: (List<Float>) -> Unit,
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
    var optionDraft by remember { mutableStateOf<EchoEqOptions?>(null) }
    val commitOptions = rememberUpdatedState(onOptionsChange)
    DisposableEffect(Unit) { onDispose { optionDraft?.let { commitOptions.value(it) } } }
    val peqEdits = rememberSignalEditSession(onParametricPreview, onParametricChange)
    val graphicEdits = rememberSignalEditSession(onBandGainsPreview, onBandGainsChange)
    var fine by rememberSaveable { mutableStateOf(false) }
    var pendingGraphicMode by rememberSaveable { mutableIntStateOf(-1) }
    var graphicIndex by rememberSaveable { mutableIntStateOf(ToneBandIndices[2]) }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var showSavedPresets by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    fun finishEdits() {
        graphicEdits.finish()
        peqEdits.finish()
        optionDraft?.let(onOptionsChange)
        optionDraft = null
    }
    val title = stringResource(L10nR.string.feature_settings_equalizer_7ccb03)
    val scheme = MaterialTheme.colorScheme
    val live = state.active && !bypassed
    val canAdjust = state.supported && !bypassed
    val defaultSaveName = state.sourceLabel?.takeIf { it.isNotBlank() }
        ?: if (state.parametric) stringResource(L10nR.string.eq_user_preset_parametric) else eqPresetLabel(state.presetId)
    val currentPresetName = remember(userPresets, activeUserPresetId) {
        userPresets.firstOrNull { it.id == activeUserPresetId && !it.fromOpra }?.name
    } ?: defaultSaveName
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SignalProcessorHeader(
            label = title, status = status,
            live = live && playing && state.processingSampleRateHz != null,
            checked = state.enabled, enabled = canAdjust,
            onCheckedChange = { finishEdits(); onEnabledChange(it) }, warning = bypassed,
            detail = if (state.parametric) stringResource(L10nR.string.eq_filter_count, state.filters.size) else null,
        )
        if (bypassed) SignalNote(stringResource(L10nR.string.signal_bypass_detail))
        state.warning?.let { SignalNote(it, error = true) }
        val canImport = canAdjust && userPresets.size < EchoEqualizerUserPresets.MaxCount
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SignalEqPresetPicker(
                currentLabel = currentPresetName,
                selectedId = state.presetId,
                userPresets = userPresets,
                activeUserId = activeUserPresetId,
                enabled = canAdjust,
                eqEnabled = state.enabled,
                onEnableEq = { finishEdits(); onEnabledChange(true) },
                onSelect = { finishEdits(); onPresetSelected(it) },
                onApplyUserPreset = { finishEdits(); onApplyUserPreset(it) },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showImport = true }, enabled = canImport) {
                Text(stringResource(L10nR.string.eq_share_import_title))
            }
        }
        if (showImport) {
            EqPresetImportDialog(
                canImport = canImport,
                onDismiss = { showImport = false },
                onImport = {
                    finishEdits()
                    onImportShareCode(it)
                    showImport = false
                },
            )
        }
        DspChoices(
            labels = listOf(
                stringResource(L10nR.string.eq_tone_mode),
                stringResource(L10nR.string.eq_fine_mode),
                stringResource(L10nR.string.eq_peq_mode),
            ),
            selected = if (state.parametric) 2 else if (fine) 1 else 0,
            enabled = canAdjust,
        ) { mode ->
            val gains = graphicEdits.currentOr(state.gainsDb)
            val tiltDb = (optionDraft ?: state.options).tiltDb
            finishEdits()
            when {
                mode == 2 && !state.parametric -> {
                    val filters = state.bands.mapNotNull { band ->
                        val gain = gains[band.index]
                        if (gain == 0f && tiltDb == 0f) null
                        else OpraEqBand(EchoEqFilterType.PeakDip, band.preciseFrequencyHz, gain, EchoEqualizerPresets.GraphicQ, null)
                    }
                    onParametricChange(filters.ifEmpty {
                        listOf(OpraEqBand(EchoEqFilterType.PeakDip, 1000f, 0f, 1f, null))
                    })
                }
                mode != 2 && state.parametric -> pendingGraphicMode = mode
                mode != 2 -> fine = mode == 1
            }
        }
        if (pendingGraphicMode >= 0 && state.parametric) {
            AlertDialog(
                onDismissRequest = { pendingGraphicMode = -1 },
                title = { Text(stringResource(L10nR.string.eq_switch_graphic_title)) },
                text = { Text(stringResource(L10nR.string.eq_switch_graphic_detail)) },
                confirmButton = { TextButton(enabled = canAdjust, onClick = {
                    finishEdits()
                    fine = pendingGraphicMode == 1
                    pendingGraphicMode = -1
                    onPresetSelected(EchoEqualizerPreset.Flat)
                }) { Text(stringResource(L10nR.string.eq_switch_graphic_action)) } },
                dismissButton = { TextButton(onClick = { pendingGraphicMode = -1 }) {
                    Text(stringResource(L10nR.string.error_log_clear_cancel))
                } },
            )
        }
        if (state.parametric) {
            SignalParametricEqualizer(state, canAdjust, live, peqEdits, onAudition)
        } else {
            SignalGraphicEqualizer(
                state = state,
                fine = fine,
                selectedIndex = graphicIndex,
                onSelectBand = { graphicEdits.finish(); graphicIndex = it },
                enabled = canAdjust,
                live = live,
                edits = graphicEdits,
            )
        }

        if (canAdjust) SignalEqHeadroom(state, onPreampChange)
        HorizontalDivider(color = scheme.outlineVariant)
        SettingsDisclosureRow(
            title = stringResource(L10nR.string.eq_more_settings),
            detail = stringResource(L10nR.string.eq_more_settings_detail),
            expanded = showOptions,
            onExpandedChange = { finishEdits(); showOptions = it },
        )
        if (showOptions) {
            if (state.mseb.active && !bypassed) {
                SignalNote(stringResource(L10nR.string.eq_mseb_combined_detail, formatEqGain(state.msebHeadroomDb)))
            }
            SignalEqAdvancedOptions(state.options, canAdjust,
                { optionDraft = null; onOptionsChange(it) }, { optionDraft = it; onOptionsPreview(it) })
            DspValueSlider(
                label = stringResource(L10nR.string.eq_preamp),
                value = state.preampDb,
                valueRange = minOf(-12f, state.preampDb)..maxOf(6f, state.preampDb),
                enabled = canAdjust,
                format = ::formatEqGain,
                onCommit = onPreampChange,
            )
        }
        TextButton(onClick = { confirmReset = true }, enabled = canAdjust,
            contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(stringResource(L10nR.string.signal_reset_equalizer))
        }
        if (confirmReset) {
            AlertDialog(
                onDismissRequest = { confirmReset = false },
                title = { Text(stringResource(L10nR.string.signal_reset_equalizer)) },
                text = { Text(stringResource(L10nR.string.eq_reset_detail)) },
                confirmButton = {
                    TextButton(enabled = canAdjust, onClick = {
                        finishEdits()
                        confirmReset = false
                        onReset()
                    }) { Text(stringResource(L10nR.string.signal_reset_equalizer)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmReset = false }) {
                        Text(stringResource(L10nR.string.error_log_clear_cancel))
                    }
                },
            )
        }
        HorizontalDivider(color = scheme.outlineVariant)
        SettingsDisclosureRow(
            title = stringResource(L10nR.string.eq_manage_presets),
            detail = stringResource(L10nR.string.eq_manage_presets_detail),
            expanded = showSavedPresets,
            onExpandedChange = { finishEdits(); showSavedPresets = it },
        )
        if (showSavedPresets) {
            val currentShare = remember(state, defaultSaveName) {
                EchoEqualizerUserPresets.capture("current", defaultSaveName, state, updatedAtEpochMs = 0L)
            }
            SignalEqUserPresets(
                presets = userPresets,
                activeId = activeUserPresetId,
                defaultSaveName = defaultSaveName,
                currentShare = currentShare,
                currentCurve = state.responseCurve,
                enabled = true,
                applyEnabled = canAdjust,
                onSave = { finishEdits(); onSaveUserPreset(it) },
                onUpdate = { finishEdits(); onUpdateUserPreset() },
                onApply = { finishEdits(); onApplyUserPreset(it) },
                onRename = onRenameUserPreset,
                onDelete = onDeleteUserPreset,
                onOpenImport = { showImport = true },
                onImportBundle = onImportBundle,
                outputDeviceLabel = outputDeviceLabel,
                outputBound = outputBound,
                onBindToOutput = onBindToOutput,
                onUnbindFromOutput = onUnbindFromOutput,
            )
        }
    }
}

@Composable
private fun SignalEqHeadroom(
    state: EchoEqualizerState,
    onPreampChange: (Float) -> Unit,
) {
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
