package app.echo.android.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoModalSheet
import app.echo.android.design.EchoSheetHeader
import app.echo.android.design.rememberEchoWindowLayout
import app.echo.android.model.playback.EchoChannelBalanceState
import app.echo.android.model.playback.EchoDspSettings
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoVolumeSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignalPathSheet(
    status: EchoPlaybackStatus,
    equalizer: EchoEqualizerState,
    balance: EchoChannelBalanceState,
    dsp: EchoDspSettings,
    volume: EchoVolumeSettings,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val window = rememberEchoWindowLayout()
    val scheme = MaterialTheme.colorScheme
    val tone = status.signalPathTone()
    val ink = tone.tint(scheme.onSurfaceVariant)
    var expandedStage by rememberSaveable { mutableIntStateOf(-1) }
    EchoModalSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().heightIn(max = (window.heightDp * 0.83f).dp)) {
            EchoSheetHeader(
                title = stringResource(R.string.feature_settings_signal_path_2fed34),
                subtitle = status.track?.title?.takeIf { it.isNotBlank() },
                leading = {
                    Box(
                        Modifier.size(44.dp)
                            .background(Brush.linearGradient(listOf(ink.copy(alpha = 0.18f), ink.copy(alpha = 0.04f))), CircleShape)
                            .border(1.dp, ink.copy(alpha = 0.20f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        SignalPathGlyph(tone, scheme.onSurfaceVariant)
                    }
                },
                trailing = {
                    IconButton(onClick = { scope.launch { sheetState.hide(); onDismiss() } }) {
                        EchoIcon(Icons.Rounded.Close, stringResource(R.string.keyboard_close))
                    }
                },
            )
            Column(
                Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            ) {
                if (status.track == null) {
                    Text(stringResource(R.string.diag_waiting_playback),
                        Modifier.padding(start = 4.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                } else {
                    val stages = signalPathStages(status, equalizer, balance, dsp, volume)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = scheme.surfaceContainer,
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.65f)),
                    ) {
                        Column(
                            Modifier.drawWithCache {
                                val wash = Brush.radialGradient(
                                    listOf(ink.copy(alpha = 0.13f), Color.Transparent),
                                    center = Offset(size.width * 0.75f, 0f),
                                    radius = (size.width * 0.76f).coerceAtLeast(1f),
                                )
                                onDrawBehind { drawRect(wash) }
                            }.padding(horizontal = 12.dp, vertical = 12.dp),
                        ) {
                            SignalPathStatus(status, tone, ink)
                            stages.forEachIndexed { index, stage ->
                                SignalPathStep(
                                    stage = stage,
                                    index = index,
                                    last = index == stages.lastIndex,
                                    expanded = expandedStage == index,
                                    accent = ink,
                                    onToggle = { expandedStage = if (expandedStage == index) -1 else index },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalPathStatus(status: EchoPlaybackStatus, tone: SignalPathTone, ink: Color) {
    val text = when {
        status.state != EchoPlaybackState.Playing -> playbackStateLabel(status.state)
        status.diagnostics.usbBitPerfectEnabled -> status.signalBitPerfectState().readout()
        !status.diagnostics.outputRouteVerified && !status.hasLiveUsbOutput() -> stringResource(R.string.path_unverified)
        tone == SignalPathTone.Converted -> stringResource(R.string.path_format_change)
        else -> return
    }
    val badgeInk = if (status.state == EchoPlaybackState.Error) MaterialTheme.colorScheme.error else ink
    Row(
        Modifier.padding(start = 4.dp, top = 2.dp, bottom = 6.dp)
            .background(badgeInk.copy(alpha = 0.10f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(5.dp).background(badgeInk, CircleShape))
        Text(text, color = badgeInk, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
    }
}
