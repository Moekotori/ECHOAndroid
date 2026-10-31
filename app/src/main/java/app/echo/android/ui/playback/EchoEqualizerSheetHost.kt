package app.echo.android.ui.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import app.echo.android.EchoAndroidViewModel
import app.echo.android.data.EchoAppSettings
import app.echo.android.feature.settings.EqualizerSheet
import app.echo.android.feature.settings.SignalEqualizer
import app.echo.android.model.playback.EchoPlaybackStatus

/** Collect EQ changes inside the drawer, without rebuilding the player on each adjustment. */
@UnstableApi
@Composable
internal fun EchoEqualizerSheetHost(
    viewModel: EchoAndroidViewModel,
    appSettings: EchoAppSettings,
    playbackStatus: EchoPlaybackStatus,
    onDismiss: () -> Unit,
) {
    val state by viewModel.equalizerState.collectAsStateWithLifecycle()
    val outputLabel = viewModel.currentOutputProfileLabel()
    EqualizerSheet(onDismiss = onDismiss) {
        SignalEqualizer(
            state = state,
            bypassed = playbackStatus.diagnostics.usbBitPerfectEnabled,
            playing = playbackStatus.isPlaying,
            userPresets = appSettings.equalizerUserPresets,
            activeUserPresetId = appSettings.equalizerActiveUserPresetId,
            onPreampChange = viewModel::setEqualizerPreamp,
            onEnabledChange = viewModel::setEqualizerEnabled,
            onPresetSelected = viewModel::setEqualizerPreset,
            onBandGainChange = viewModel::setEqualizerBandGain,
            onReset = viewModel::resetEqualizer,
            onParametricChange = viewModel::setParametricFilters,
            onParametricPreview = viewModel::previewParametricFilters,
            onSaveUserPreset = viewModel::saveCurrentEqualizerPreset,
            onUpdateUserPreset = viewModel::updateCurrentEqualizerUserPreset,
            onApplyUserPreset = viewModel::applyEqualizerUserPreset,
            onRenameUserPreset = viewModel::renameEqualizerUserPreset,
            onDeleteUserPreset = viewModel::deleteEqualizerUserPreset,
            onImportShareCode = viewModel::importEqualizerShareCode,
            onImportBundle = viewModel::importEqualizerBundle,
            outputDeviceLabel = outputLabel,
            outputBound = appSettings.equalizerDevicePresetIds.containsKey(viewModel.currentOutputProfileKey()),
            onBindToOutput = viewModel::bindActiveEqualizerPresetToOutput,
            onUnbindFromOutput = viewModel::unbindEqualizerPresetFromOutput,
        )
    }
}
