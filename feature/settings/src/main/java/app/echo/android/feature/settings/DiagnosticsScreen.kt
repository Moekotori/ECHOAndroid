package app.echo.android.feature.settings

import app.echo.android.feature.settings.R as L10nR

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import app.echo.android.design.LocalEchoContentMaxWidth
import app.echo.android.model.playback.*
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    status: EchoPlaybackStatus,
    positionFlow: StateFlow<PlaybackPositionState>,
    equalizerState: EchoEqualizerState,
    opraState: OpraHeadphoneCorrectionState,
    onEqualizerEnabledChange: (Boolean) -> Unit,
    onEqualizerPresetSelected: (String) -> Unit,
    onEqualizerBandGainChange: (Int, Float) -> Unit,
    onEqualizerReset: () -> Unit,
    onOpraQueryChange: (String) -> Unit,
    onOpraSearch: () -> Unit,
    onOpraRefresh: () -> Unit,
    onOpraPresetSelected: (String) -> Unit,
    onOpraApplySelected: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.diag_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Medium)
                    Text(playbackStateLabel(status.state), style = MaterialTheme.typography.labelMedium, color = if (status.isPlaying) scheme.primary else scheme.onSurfaceVariant)
                }
                SecondaryTabRow(selectedTabIndex = selectedTab, containerColor = scheme.background, contentColor = scheme.primary) {
                    labels.forEachIndexed { index, label ->
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }, unselectedContentColor = scheme.onSurfaceVariant, text = {
                            Text(label, fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal)
                        })
                    }
                }
            }
            Column(
                Modifier.widthIn(max = LocalEchoContentMaxWidth.current).fillMaxWidth().weight(1f)
                    .verticalScroll(scrollStates[selectedTab]).padding(horizontal = 24.dp)
                    .padding(top = 24.dp, bottom = 188.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                val error = status.diagnostics.lastError?.message ?: status.diagnostics.usbLastRequestError?.message
                if (error != null && selectedTab != 2) {
                    Column(Modifier.fillMaxWidth().background(scheme.errorContainer).padding(12.dp)) {
                        Text(error, color = scheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Normal)
                        TextButton(onClick = { selectedTab = 2 }) { Text(labels[2], color = scheme.onErrorContainer) }
                    }
                }
                when (selectedTab) {
                    0 -> SignalOverview(status, equalizerState, onAdjust = { selectedTab = 1 }, onDiagnostics = { selectedTab = 2 })
                    1 -> {
                        SignalEqualizer(
                            state = equalizerState,
                            onEnabledChange = onEqualizerEnabledChange,
                            onPresetSelected = onEqualizerPresetSelected,
                            onBandGainChange = onEqualizerBandGainChange,
                            onReset = onEqualizerReset,
                        )
                        SignalHeadphoneCorrection(
                            state = opraState,
                            onQueryChange = onOpraQueryChange,
                            onSearch = onOpraSearch,
                            onRefresh = onOpraRefresh,
                            onPresetSelected = onOpraPresetSelected,
                            onApplySelected = onOpraApplySelected,
                        )
                    }
                    else -> {
                        HealthPanel(status)
                        UsbOutputPanel(status)
                        CurrentStreamPanel(status, positionFlow)
                    }
                }
            }
        }
    }
}
