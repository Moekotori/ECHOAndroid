package app.echo.android.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoOutputDeviceKind
import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.dsdFamilyLabel
import app.echo.android.model.playback.isDsdSource

@Composable
internal fun SignalDeviceCard(status: EchoPlaybackStatus) {
    val d = status.diagnostics
    val scheme = MaterialTheme.colorScheme
    val kind = if (d.usbExclusiveStreaming) EchoOutputDeviceKind.Usb else EchoOutputDeviceKind.fromId(d.outputDeviceKind)
    val deviceName = (if (d.usbExclusiveStreaming) d.usbDeviceName else d.outputDeviceName)
        ?.takeIf { it.isNotBlank() }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = scheme.surface,
        contentColor = scheme.onSurface,
        border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.16f)),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(scheme.primary.copy(alpha = 0.07f), scheme.surface)))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = scheme.primary.copy(alpha = 0.10f),
                    contentColor = scheme.primary,
                    border = BorderStroke(1.dp, scheme.primary.copy(alpha = 0.12f)),
                ) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            when (kind) {
                                EchoOutputDeviceKind.Usb -> Icons.Rounded.Usb
                                EchoOutputDeviceKind.Bluetooth -> Icons.Rounded.Bluetooth
                                EchoOutputDeviceKind.Wired -> Icons.Rounded.Headphones
                                else -> Icons.Rounded.Speaker
                            },
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.diag_output_end), style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                    Text(
                        deviceName ?: outputDeviceKindLabel(kind.id),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (deviceName != null) SignalNote(outputDeviceKindLabel(kind.id))
                }
            }
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.45f))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.path_source_format), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DeviceMetric(
                        stringResource(R.string.diag_sample_rate),
                        if (d.isDsdSource()) d.dsdFamilyLabel() ?: "—"
                        else (d.sampleRateHz ?: status.track?.sampleRateHz)?.takeIf { it > 0 }?.let(::formatSampleRate) ?: "—",
                        Modifier.weight(1f),
                    )
                    VerticalDivider(color = scheme.outlineVariant.copy(alpha = 0.40f))
                    DeviceMetric(stringResource(R.string.diag_bit_depth), d.bitDepth?.takeIf { it > 0 }?.let { "$it bit" } ?: "—", Modifier.weight(1f))
                    VerticalDivider(color = scheme.outlineVariant.copy(alpha = 0.40f))
                    DeviceMetric(stringResource(R.string.diag_channels), d.channelCount?.takeIf { it > 0 }?.let(::formatChannels) ?: "—", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DeviceMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
