package app.echo.android.feature.settings

import app.echo.android.feature.settings.R as L10nR

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoMotion
import app.echo.android.design.LocalEchoContentMaxWidth
import app.echo.android.model.playback.*
import kotlinx.coroutines.flow.StateFlow

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
    onOpraQueryChange: (String) -> Unit,
    onOpraSearch: () -> Unit,
    onOpraRefresh: () -> Unit,
    onOpraPresetSelected: (String) -> Unit,
    onOpraApplySelected: () -> Unit,
    bluetoothCodecNeedsPermission: Boolean = false,
    onRequestBluetoothCodecPermission: () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var soundPanel by rememberSaveable { mutableIntStateOf(0) }
    val scrollStates = listOf(rememberScrollState(), rememberScrollState(), rememberScrollState())
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
                    SignalSoundModeRow(selectedIndex = selectedTab, labels = labels, onSelect = { selectedTab = it })
                }
            }
            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier.widthIn(max = LocalEchoContentMaxWidth.current)
                    .fillMaxWidth().weight(1f).clipToBounds(),
                transitionSpec = { EchoMotion.tabSwitch(targetState > initialState) },
                label = "SignalTabs",
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
                            TextButton(onClick = { selectedTab = 2 }) { Text(labels[2], color = scheme.error) }
                        }
                    }
                    when (tab) {
                        0 -> SignalOverview(status, equalizerState, channelBalanceState, onAdjust = { selectedTab = 1 }, onDiagnostics = { selectedTab = 2 })
                        1 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                SignalSoundModeRow(
                                    selectedIndex = soundPanel,
                                    labels = listOf(
                                        stringResource(L10nR.string.feature_settings_equalizer_7ccb03),
                                        stringResource(L10nR.string.feature_settings_headphone_correction_491ce5),
                                        stringResource(L10nR.string.channel_balance),
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
                                        onPreampChange = onEqualizerPreampChange,
                                        onEnabledChange = onEqualizerEnabledChange,
                                        onPresetSelected = onEqualizerPresetSelected,
                                        onBandGainChange = onEqualizerBandGainChange,
                                        onReset = onEqualizerReset,
                                    )
                                    else if (panel == 1) SignalHeadphoneCorrection(
                                        state = opraState,
                                        equalizer = equalizerState,
                                        bypassed = status.diagnostics.usbBitPerfectEnabled,
                                        onQueryChange = onOpraQueryChange,
                                        onSearch = onOpraSearch,
                                        onRefresh = onOpraRefresh,
                                        onPresetSelected = onOpraPresetSelected,
                                        onApplySelected = onOpraApplySelected,
                                    )
                                    else SignalChannelBalance(
                                        state = channelBalanceState,
                                        bypassed = status.diagnostics.usbBitPerfectEnabled,
                                        playing = status.isPlaying,
                                        onStateChange = onChannelBalanceChange,
                                        onReset = onChannelBalanceReset,
                                    )
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
