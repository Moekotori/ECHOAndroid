package app.echo.android.feature.settings

import app.echo.android.feature.settings.R as L10nR

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoContentMaxWidth
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.animateSilkToPage
import app.echo.android.design.rememberContentPagerNestedScroll
import app.echo.android.design.rememberSilkPagerFlingBehavior
import app.echo.android.model.playback.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    onApplyUserPreset: (String) -> Unit = {},
    onRenameUserPreset: (String, String) -> Unit = { _, _ -> },
    onDeleteUserPreset: (String) -> Unit = {},
    onImportShareCode: (String) -> Unit = {},
    onBindPresetToOutput: () -> Unit = {},
    outputDeviceLabel: String? = null,
    outputPresetBound: Boolean = false,
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
) {
    val pagerState = rememberPagerState { 3 }
    var soundPanel by rememberSaveable { mutableIntStateOf(0) }
    val scrollStates = listOf(rememberScrollState(), rememberScrollState(), rememberScrollState())
    val scope = rememberCoroutineScope()
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    val innerFling = rememberSilkPagerFlingBehavior(pagerState)
    val innerNestedScroll = rememberContentPagerNestedScroll(pagerState, innerFling)
    fun selectTab(index: Int) {
        scope.launch { pagerState.animateSilkToPage(index, lightweight) }
    }
    val labels = listOf(
        stringResource(L10nR.string.feature_settings_signal_path_2fed34),
        stringResource(L10nR.string.feature_settings_sound_3f2864),
        stringResource(L10nR.string.feature_settings_diagnostics_b6e487),
    )
    val scheme = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalContentColor provides scheme.onBackground) {
        Column(
            Modifier.fillMaxSize().background(scheme.background).statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = LocalEchoContentMaxWidth.current).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(L10nR.string.diag_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (status.state == EchoPlaybackState.Error) scheme.errorContainer.copy(alpha = 0.24f) else scheme.surfaceContainerHigh,
                        contentColor = if (status.state == EchoPlaybackState.Error) scheme.error else scheme.primary,
                    ) {
                        Text(playbackStateLabel(status.state), Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Box(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
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
                modifier = Modifier.fillMaxWidth().weight(1f),
                beyondViewportPageCount = if (lightweight) 0 else 1,
                flingBehavior = innerFling,
                pageNestedScrollConnection = innerNestedScroll,
            ) { tab ->
                Column(
                    Modifier.fillMaxSize()
                        .verticalScroll(scrollStates[tab]).padding(horizontal = 24.dp)
                        .padding(top = 20.dp, bottom = 188.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    val error = status.diagnostics.lastError?.message ?: status.diagnostics.usbLastRequestError?.message
                    if (error != null && tab != 2) {
                        Row(
                            Modifier.fillMaxWidth().background(scheme.errorContainer.copy(alpha = 0.24f), RoundedCornerShape(16.dp)).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(error, Modifier.weight(1f), color = scheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal)
                            TextButton(onClick = { selectTab(2) }) { Text(labels[2], color = scheme.error) }
                        }
                    }
                    when (tab) {
                        0 -> SignalOverview(status, equalizerState, channelBalanceState, onAdjust = { selectTab(1) }, onDiagnostics = { selectTab(2) })
                        1 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SignalSoundTabs(
                                    selectedIndex = soundPanel,
                                    labels = listOf(
                                        stringResource(L10nR.string.feature_settings_equalizer_7ccb03),
                                        stringResource(L10nR.string.feature_settings_headphone_correction_491ce5),
                                        stringResource(L10nR.string.channel_balance),
                                        stringResource(L10nR.string.dsp_title),
                                    ),
                                    onSelect = { soundPanel = it },
                                )
                                AnimatedContent(
                                    targetState = soundPanel,
                                    modifier = Modifier.fillMaxWidth(),
                                    transitionSpec = { EchoMotion.tabSwitch(targetState > initialState) },
                                    label = "SignalSoundPanel",
                                ) { panel ->
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
                                        onSaveUserPreset = onSaveUserPreset,
                                        onApplyUserPreset = onApplyUserPreset,
                                        onRenameUserPreset = onRenameUserPreset,
                                        onDeleteUserPreset = onDeleteUserPreset,
                                        onImportShareCode = onImportShareCode,
                                        outputDeviceLabel = outputDeviceLabel,
                                        outputBound = outputPresetBound,
                                        onBindToOutput = onBindPresetToOutput,
                                    )
                                    else if (panel == 1) SignalHeadphoneCorrection(
                                        state = opraState,
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
                                    else SignalDspPanel(dspSettings, status, replayGainScan, onDspSettings, onReplayGain, onReplayGainMode, onReplayGainScan)
                                }
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
