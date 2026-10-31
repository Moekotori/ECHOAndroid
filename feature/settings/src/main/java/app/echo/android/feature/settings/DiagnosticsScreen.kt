package app.echo.android.feature.settings

import app.echo.android.feature.settings.R as L10nR

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.LocalEchoContentMaxWidth
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.echoPageBackgroundColor
import app.echo.android.design.animateSilkToPage
import app.echo.android.design.rememberContentPagerNestedScroll
import app.echo.android.design.echoPagerEdgeSwipe
import app.echo.android.design.rememberSilkPagerFlingBehavior
import app.echo.android.model.playback.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun DiagnosticsScreen(
    status: EchoPlaybackStatus,
    positionFlow: StateFlow<PlaybackPositionState>,
    equalizerState: EchoEqualizerState,
    channelBalanceState: EchoChannelBalanceState,
    opraState: OpraHeadphoneCorrectionState,
    onEqualizerEnabledChange: (Boolean) -> Unit,
    onEqualizerPresetSelected: (String) -> Unit,
    onEqualizerBandGainChange: (Int, Float) -> Unit,
    onEqualizerReset: () -> Unit,
    onEqualizerPreampChange: (Float) -> Unit,
    onChannelBalanceChange: (EchoChannelBalanceState) -> Unit,
    onChannelBalanceReset: () -> Unit,
    onOpraBrandSelected: (String?) -> Unit,
    onOpraQueryChange: (String) -> Unit,
    onOpraSearch: () -> Unit,
    onOpraRefresh: () -> Unit,
    onOpraPresetSelected: (String) -> Unit,
    onOpraApplySelected: () -> Unit,
    userPresets: List<EchoEqualizerUserPreset> = emptyList(),
    activeUserPresetId: String? = null,
    opraLastQuery: String = "",
    onSaveUserPreset: (String) -> Unit = {},
    onUpdateUserPreset: () -> Unit = {},
    onApplyUserPreset: (String) -> Unit = {},
    onRenameUserPreset: (String, String) -> Unit = { _, _ -> },
    onDeleteUserPreset: (String) -> Unit = {},
    onImportShareCode: (String) -> Unit = {},
    onImportBundle: suspend (List<app.echo.android.model.playback.EchoEqualizerUserPreset>, app.echo.android.model.playback.EchoEqImportConflict) -> Unit = { _, _ -> },
    onBindPresetToOutput: () -> Unit = {},
    onUnbindPresetFromOutput: () -> Unit = {},
    outputDeviceLabel: String? = null,
    outputPresetBound: Boolean = false,
    outputAudioProfileBound: Boolean = false,
    onSaveOutputAudioProfile: () -> Unit = {},
    onRemoveOutputAudioProfile: () -> Unit = {},
    onToggleOpraFavorite: () -> Unit = {},
    dspSettings: EchoDspSettings,
    replayGainScan: EchoReplayGainScanState,
    onDspSettings: (EchoDspSettings) -> Unit,
    onReplayGain: (Boolean, Float) -> Unit,
    onReplayGainMode: (EchoReplayGainMode) -> Unit,
    onReplayGainScan: () -> Unit,
    onParametricChange: (List<OpraEqBand>) -> Unit,
    bluetoothCodecNeedsPermission: Boolean = false,
    onRequestBluetoothCodecPermission: () -> Unit = {},
    onSwipeToConnect: () -> Unit = {},
    soundRequestId: Int = 0,
    isActive: Boolean = true,
    onMsebChange: (EchoMsebState) -> Unit = {},
    msebPersonal: MsebPresetActions = MsebPresetActions(),
    onParametricPreview: ((List<OpraEqBand>) -> Unit)? = null,
    onMsebPreview: ((EchoMsebState) -> Unit)? = null,
    volumeSettings: EchoVolumeSettings = EchoVolumeSettings(),
    onVolumeSettings: (EchoVolumeSettings) -> Unit = {},
) {
    val pagerState = rememberPagerState { 3 }
    // -1 is the sound-tool list; controls are mounted only after choosing a tool.
    var soundPanel by rememberSaveable { mutableIntStateOf(-1) }
    var handledSoundRequestId by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(soundRequestId) {
        if (soundRequestId != 0 && soundRequestId != handledSoundRequestId) {
            soundPanel = -1
            pagerState.scrollToPage(1)
            handledSoundRequestId = soundRequestId
        }
    }
    val scrollStates = listOf(rememberScrollState(), rememberScrollState(), rememberScrollState())
    val scope = rememberCoroutineScope()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val innerFling = rememberSilkPagerFlingBehavior(pagerState)
    val innerNestedScroll = rememberContentPagerNestedScroll(pagerState, innerFling)
    fun selectTab(index: Int) {
        soundPanel = -1
        scope.launch { pagerState.animateSilkToPage(index, lightweight) }
    }
    val labels = listOf(
        stringResource(L10nR.string.feature_settings_signal_path_2fed34),
        stringResource(L10nR.string.feature_settings_sound_3f2864),
        stringResource(L10nR.string.feature_settings_diagnostics_b6e487),
    )
    val scheme = MaterialTheme.colorScheme
    val soundLabels = listOf(
        stringResource(L10nR.string.peq_workspace_title),
        stringResource(L10nR.string.feature_settings_headphone_correction_491ce5),
        stringResource(L10nR.string.channel_balance),
        stringResource(L10nR.string.dsp_title),
        stringResource(L10nR.string.mseb_title),
        stringResource(L10nR.string.volume_title),
    )
    val errorContent: @Composable () -> Unit = {
        val error = status.diagnostics.lastError?.message ?: status.diagnostics.usbLastRequestError?.message
        if (error != null) {
            Row(
                Modifier.fillMaxWidth().background(scheme.errorContainer.copy(alpha = 0.24f), RoundedCornerShape(16.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(error, Modifier.weight(1f), color = scheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal)
                TextButton(onClick = { selectTab(2) }) { Text(labels[2], color = scheme.error) }
            }
        }
    }
    CompositionLocalProvider(LocalContentColor provides scheme.onBackground) {
        SignalSoundNavigation(
            selectedPanel = soundPanel,
            labels = soundLabels,
            isActive = isActive,
            onBack = { soundPanel = -1 },
            scrollingTool = 1,
            onNavigateUp = if (soundPanel == 1 && opraState.selectedBrandId != null) {
                { onOpraBrandSelected(null) }
            } else null,
            tool = { panel ->
                errorContent()
                if (panel == 0) SignalEqualizer(
                    state = equalizerState,
                    bypassed = status.diagnostics.usbBitPerfectEnabled,
                    playing = status.isPlaying,
                    userPresets = userPresets,
                    activeUserPresetId = activeUserPresetId,
                    onPreampChange = onEqualizerPreampChange,
                    onEnabledChange = onEqualizerEnabledChange,
                    onPresetSelected = onEqualizerPresetSelected,
                    onBandGainChange = onEqualizerBandGainChange,
                    onReset = onEqualizerReset,
                    onParametricChange = onParametricChange,
                    onParametricPreview = onParametricPreview,
                    onSaveUserPreset = onSaveUserPreset,
                    onUpdateUserPreset = onUpdateUserPreset,
                    onApplyUserPreset = onApplyUserPreset,
                    onRenameUserPreset = onRenameUserPreset,
                    onDeleteUserPreset = onDeleteUserPreset,
                    onImportShareCode = onImportShareCode,
                    onImportBundle = onImportBundle,
                    outputDeviceLabel = outputDeviceLabel,
                    outputBound = outputPresetBound,
                    onBindToOutput = onBindPresetToOutput,
                    onUnbindFromOutput = onUnbindPresetFromOutput,
                )
                else if (panel == 1) SignalHeadphoneCorrection(
                    state = opraState,
                    playing = status.isPlaying,
                    equalizer = equalizerState,
                    bypassed = status.diagnostics.usbBitPerfectEnabled,
                    userPresets = userPresets,
                    lastQuery = opraLastQuery,
                    onBrandSelected = onOpraBrandSelected,
                    onQueryChange = onOpraQueryChange,
                    onSearch = onOpraSearch,
                    onRefresh = onOpraRefresh,
                    onPresetSelected = onOpraPresetSelected,
                    onApplySelected = onOpraApplySelected,
                    onToggleFavorite = onToggleOpraFavorite,
                    onApplyUserPreset = onApplyUserPreset,
                )
                else if (panel == 2) SignalChannelBalance(
                    state = channelBalanceState,
                    bypassed = status.diagnostics.usbBitPerfectEnabled,
                    playing = status.isPlaying,
                    onStateChange = onChannelBalanceChange,
                    onReset = onChannelBalanceReset,
                )
                else if (panel == 3) SignalDspPanel(dspSettings, status, replayGainScan, onDspSettings, onReplayGain, onReplayGainMode, onReplayGainScan)
                else if (panel == 4) SignalMsebPanel(equalizerState, status, onMsebChange, onMsebPreview, msebPersonal)
                else SignalVolumePanel(volumeSettings, status, dspSettings, replayGainScan,
                    onVolumeSettings, onDspSettings, onReplayGain, onReplayGainMode, onReplayGainScan, outputDeviceLabel)
            },
        ) {
            Column(
                Modifier.fillMaxSize().background(echoPageBackgroundColor()).statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = LocalEchoContentMaxWidth.current).fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(L10nR.string.diag_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (status.state == EchoPlaybackState.Error) scheme.errorContainer.copy(alpha = 0.24f) else scheme.surfaceContainerHigh,
                            contentColor = if (status.state == EchoPlaybackState.Error) scheme.error else scheme.primary,
                        ) {
                            Text(playbackStateLabel(status.state), Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Box(Modifier.padding(horizontal = 20.dp)) {
                        SignalSoundModeRow(
                            selectedIndex = pagerState.currentPage,
                            labels = labels,
                            onSelect = ::selectTab,
                            selectedProgress = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                        )
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().weight(1f).echoPagerEdgeSwipe(pagerState, onSwipeToConnect),
                    beyondViewportPageCount = if (lightweight) 0 else 1,
                    flingBehavior = innerFling,
                    pageNestedScrollConnection = innerNestedScroll,
                ) { tab ->
                    Column(Modifier.widthIn(max = LocalEchoContentMaxWidth.current).fillMaxSize()) {
                        Column(
                            Modifier.fillMaxWidth().weight(1f)
                                .verticalScroll(scrollStates[tab]).padding(horizontal = 24.dp)
                                .padding(top = 12.dp, bottom = 188.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            if (tab != 2) errorContent()
                            when (tab) {
                                0 -> SignalOverview(status, equalizerState, channelBalanceState, dspSettings, onAdjust = { selectTab(1) }, onDiagnostics = { selectTab(2) }, volume = volumeSettings)
                                1 -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                        SignalSoundMenu(soundLabels, onSelect = { soundPanel = it })
                                        OutputAudioProfileControl(outputDeviceLabel, outputAudioProfileBound,
                                            status.diagnostics.usbBitPerfectEnabled, onSaveOutputAudioProfile, onRemoveOutputAudioProfile)
                                    }
                                }
                                else -> {
                                    HealthPanel(
                                        status = status,
                                        bluetoothCodecNeedsPermission = bluetoothCodecNeedsPermission,
                                        onRequestBluetoothCodecPermission = onRequestBluetoothCodecPermission,
                                    )
                                    UsbOutputPanel(status)
                                    CurrentStreamPanel(status, positionFlow)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
