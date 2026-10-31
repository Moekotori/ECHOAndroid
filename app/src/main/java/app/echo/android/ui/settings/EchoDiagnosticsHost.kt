package app.echo.android.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import app.echo.android.EchoAndroidViewModel
import app.echo.android.data.EchoAppSettings
import app.echo.android.feature.settings.DiagnosticsScreen
import app.echo.android.model.playback.EchoPlaybackStatus

/** Sound-page wiring lives outside the root pager to keep generated Compose methods bounded. */
@UnstableApi
@Composable
internal fun EchoDiagnosticsHost(
    viewModel: EchoAndroidViewModel,
    appSettings: EchoAppSettings,
    playbackStatus: EchoPlaybackStatus,
    isActive: Boolean,
    soundRequestId: Int,
    equalizerRequestId: Int,
    onCloseRequestedEqualizer: () -> Unit,
    onSwipeToConnect: () -> Unit,
    bluetoothCodecNeedsPermission: Boolean,
    onRequestBluetoothCodecPermission: () -> Unit,
) {
    val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()
    val channelBalanceState by viewModel.channelBalanceState.collectAsStateWithLifecycle()
    val opraState by viewModel.opraState.collectAsStateWithLifecycle()
    val replayGainScan by viewModel.replayGainScanState.collectAsStateWithLifecycle()
    DiagnosticsScreen(
        isActive = isActive,
        soundRequestId = soundRequestId,
        equalizerRequestId = equalizerRequestId,
        onCloseRequestedEqualizer = onCloseRequestedEqualizer,
        onSwipeToConnect = onSwipeToConnect,
        status = playbackStatus,
        positionFlow = viewModel.playbackPosition,
        equalizerState = equalizerState,
        dspSettings = appSettings.dsp,
        volumeSettings = appSettings.volume,
        onVolumeSettings = viewModel::setVolumeSettings,
        replayGainScan = replayGainScan,
        onDspSettings = viewModel::setDspSettings,
        onReplayGain = viewModel::setReplayGain,
        onReplayGainMode = viewModel::setReplayGainMode,
        onReplayGainScan = viewModel::scanReplayGainForCurrentTrack,
        onParametricChange = viewModel::setParametricFilters,
        onParametricPreview = viewModel::previewParametricFilters,
        onMsebPreview = viewModel::previewMseb,
        channelBalanceState = channelBalanceState,
        opraState = opraState,
        onEqualizerEnabledChange = viewModel::setEqualizerEnabled,
        onEqualizerPresetSelected = viewModel::setEqualizerPreset,
        onEqualizerBandGainChange = viewModel::setEqualizerBandGain,
        onEqualizerReset = viewModel::resetEqualizer,
        onEqualizerPreampChange = viewModel::setEqualizerPreamp,
        onMsebChange = viewModel::setMseb,
        msebPersonal = viewModel.msebPresets(appSettings.msebUserPresets),
        onChannelBalanceChange = viewModel::setChannelBalance,
        onChannelBalanceReset = viewModel::resetChannelBalance,
        onOpraBrandSelected = viewModel::browseOpraBrand,
        onOpraQueryChange = viewModel::updateOpraQuery,
        onOpraSearch = { viewModel.searchOpraHeadphoneCorrections(refresh = false) },
        onOpraRefresh = { viewModel.searchOpraHeadphoneCorrections(refresh = true) },
        onOpraPresetSelected = viewModel::selectOpraPreset,
        onOpraApplySelected = viewModel::applySelectedOpraPreset,
        userPresets = appSettings.equalizerUserPresets,
        activeUserPresetId = appSettings.equalizerActiveUserPresetId,
        opraLastQuery = appSettings.opraLastQuery,
        onSaveUserPreset = viewModel::saveCurrentEqualizerPreset,
        onUpdateUserPreset = viewModel::updateCurrentEqualizerUserPreset,
        onApplyUserPreset = viewModel::applyEqualizerUserPreset,
        onRenameUserPreset = viewModel::renameEqualizerUserPreset,
        onDeleteUserPreset = viewModel::deleteEqualizerUserPreset,
        onImportShareCode = viewModel::importEqualizerShareCode,
        onImportBundle = viewModel::importEqualizerBundle,
        onSaveOutputAudioProfile = viewModel::saveOutputAudioProfile,
        onRemoveOutputAudioProfile = viewModel::removeOutputAudioProfile,
        outputAudioProfileBound = appSettings.outputAudioProfileKeys.contains(viewModel.currentOutputProfileKey()),
        onBindPresetToOutput = viewModel::bindActiveEqualizerPresetToOutput,
        onUnbindPresetFromOutput = viewModel::unbindEqualizerPresetFromOutput,
        outputDeviceLabel = viewModel.currentOutputProfileLabel(),
        outputPresetBound = appSettings.equalizerDevicePresetIds.containsKey(viewModel.currentOutputProfileKey()),
        onToggleOpraFavorite = viewModel::toggleStarredOpraPreset,
        bluetoothCodecNeedsPermission = bluetoothCodecNeedsPermission,
        onRequestBluetoothCodecPermission = onRequestBluetoothCodecPermission,
    )
}
