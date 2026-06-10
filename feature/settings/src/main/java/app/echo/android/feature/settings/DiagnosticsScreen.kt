package app.echo.android.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoAccent
import app.echo.android.design.EchoAccentText
import app.echo.android.design.EchoGlassBorder
import app.echo.android.design.EchoHomeBlue
import app.echo.android.design.EchoHomeMist
import app.echo.android.design.LocalEchoDarkTheme
import app.echo.android.design.EchoPlaceholderLine
import app.echo.android.design.EchoSectionTitle
import app.echo.android.design.PageChrome
import app.echo.android.design.RoonInk
import app.echo.android.design.RoonMuted
import app.echo.android.design.echoDarkGlassBorder
import app.echo.android.design.echoGlassContainerBrush
import app.echo.android.design.formatDuration
import app.echo.android.model.playback.EchoEqualizerState
import app.echo.android.model.playback.EchoPlaybackState
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.OpraHeadphoneCorrectionState

@Composable
fun DiagnosticsScreen(
    status: EchoPlaybackStatus,
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
    val diagnostics = status.diagnostics
    val bufferSeconds = "${diagnostics.bufferedMs / 1000}s"
    val codec = diagnostics.codec ?: "Media3"
    val lastCommand = commandLabel(diagnostics.lastCommand)
    val dspActive = diagnostics.offloadActive || equalizerState.active
    PageChrome(title = "信号", subtitle = "音频链路与解码状态", badge = "状态", scrollable = true) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SignalHeroCard(
                status = status,
                output = diagnostics.outputRoute,
                codec = codec,
                buffer = bufferSeconds,
                lastCommand = lastCommand,
            )
            SignalFlowPanel(
                codec = codec,
                output = diagnostics.outputRoute,
                dspActive = dspActive,
                diagnostics = diagnostics,
            )
            EqualizerPanel(
                state = equalizerState,
                opraState = opraState,
                onEnabledChange = onEqualizerEnabledChange,
                onPresetSelected = onEqualizerPresetSelected,
                onBandGainChange = onEqualizerBandGainChange,
                onReset = onEqualizerReset,
                onOpraQueryChange = onOpraQueryChange,
                onOpraSearch = onOpraSearch,
                onOpraRefresh = onOpraRefresh,
                onOpraPresetSelected = onOpraPresetSelected,
                onOpraApplySelected = onOpraApplySelected,
            )
            UsbOutputPanel(status = status)
            CurrentStreamPanel(
                status = status,
                lastCommand = lastCommand,
                requestToken = diagnostics.requestToken,
            )
            HealthPanel(status = status)
        }
    }
}

@Composable
private fun UsbOutputPanel(status: EchoPlaybackStatus) {
    val diagnostics = status.diagnostics
    val scheme = MaterialTheme.colorScheme
    val dark = LocalEchoDarkTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (dark) echoGlassContainerBrush(0.92f) else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.64f), EchoHomeMist.copy(alpha = 0.42f))))
            .border(
                if (dark) echoDarkGlassBorder() else BorderStroke(1.dp, EchoGlassBorder.copy(alpha = 0.84f)),
                RoundedCornerShape(20.dp),
            )
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EchoSectionTitle(
                "USB 输出",
                if (diagnostics.usbConnected) "已连接" else "未连接",
            )
            UsbOutputLine("设备", diagnostics.usbDeviceName ?: "无 USB DAC")
            UsbOutputLine(
                "链路",
                when {
                    diagnostics.usbBitPerfectActive -> "USB 独占 · bit-perfect"
                    diagnostics.usbExclusiveEnabled && diagnostics.usbHostPermissionPending -> "等待 USB 授权"
                    diagnostics.usbExclusiveEnabled && diagnostics.usbHostPermissionGranted && diagnostics.usbAudioHasIsochronousOut -> "USB 已授权 · ISO 待驱动"
                    diagnostics.usbExclusiveEnabled && diagnostics.usbHostPermissionGranted -> "USB 已授权 · 待接管"
                    diagnostics.usbExclusiveEnabled && diagnostics.usbConnected -> "USB 未授权 · 未独占"
                    diagnostics.usbHostPermissionGranted -> "USB host 已授权"
                    diagnostics.usbHostPermissionPending -> "等待 USB 授权"
                    diagnostics.usbBitPerfectSupported -> "支持 bit-perfect"
                    diagnostics.usbConnected -> "Android mixer"
                    else -> "Media3 / AudioTrack"
                },
            )
            UsbOutputLine(
                "采样率",
                formatUsbSampleRates(diagnostics.usbSupportedSampleRates),
            )
            UsbOutputLine(
                "请求",
                diagnostics.usbLastRequestedSampleRateHz?.let { "${it}Hz" } ?: "未请求",
            )
            if (diagnostics.usbConnected) {
                UsbOutputLine(
                    "USB 权限",
                    when {
                        diagnostics.usbHostPermissionGranted -> "已授权"
                        diagnostics.usbHostPermissionPending -> "等待确认"
                        diagnostics.usbExclusiveEnabled -> "未授权"
                        else -> "未请求"
                    },
                )
            }
            diagnostics.usbAudioClass?.let { UsbOutputLine("UAC", it) }
            if (diagnostics.usbAudioInterfaceCount > 0) {
                UsbOutputLine(
                    "接口",
                    "${diagnostics.usbAudioInterfaceCount} audio / ${diagnostics.usbAudioStreamingInterfaceCount} stream",
                )
            }
            diagnostics.usbAudioEndpointSummary?.let { UsbOutputLine("端点", it) }
            if (diagnostics.usbAudioHasIsochronousOut || diagnostics.usbAudioHasFeedbackEndpoint) {
                UsbOutputLine(
                    "传输",
                    when {
                        diagnostics.usbAudioHasIsochronousOut && diagnostics.usbAudioHasFeedbackEndpoint -> "iso OUT + feedback"
                        diagnostics.usbAudioHasIsochronousOut -> "iso OUT"
                        else -> "feedback"
                    },
                )
            }
            diagnostics.usbAudioDescriptorError?.let { error ->
                UsbOutputLine("Descriptor", error)
            }
            diagnostics.usbLastRequestError?.let { error ->
                UsbOutputLine("回退", error.message)
            }
        }
    }
}

@Composable
private fun UsbOutputLine(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun formatUsbSampleRates(sampleRates: List<Int>): String =
    if (sampleRates.isEmpty()) {
        "未上报"
    } else {
        sampleRates.take(6).joinToString(" / ") { "${it / 1000}k" } +
            if (sampleRates.size > 6) " ..." else ""
    }

