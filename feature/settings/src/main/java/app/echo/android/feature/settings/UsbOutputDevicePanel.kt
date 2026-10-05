package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

/** The DAC and the software evidence form one quiet instrument panel. No decorative polling. */
@Composable
internal fun UsbOutputDevicePanel(output: UsbOutputPresentation) {
    val scheme = MaterialTheme.colorScheme
    val headline = stringResource(output.headlineResource())
    Surface(Modifier.fillMaxWidth().padding(horizontal = SettingsContentInset, vertical = 4.dp),
        shape = RoundedCornerShape(28.dp), color = scheme.surfaceContainer) {
    Column(
        Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(18.dp), color = scheme.onSurface.copy(alpha = 0.045f)) {
                EchoIcon(SettingsCategoryIcons.Usb, null, Modifier.padding(14.dp).size(30.dp),
                    tint = scheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(output.audioClass?.let { "USB DAC · $it" } ?: "USB DAC",
                    style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Text(output.deviceName ?: stringResource(
                    if (output.connected) R.string.diag_usb_recognized else R.string.usb_page_connect_title),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val statusColor = if (output.failed) scheme.error else scheme.onSurfaceVariant
            Box(Modifier.size(6.dp).background(statusColor, CircleShape))
            Text(headline, style = MaterialTheme.typography.labelLarge, color = statusColor)
        }
        if (output.connected) UsbConnectionSteps(output)
        if (output.connected) {
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.55f))
        val clock = (output.clockHz ?: output.transferRateHz)?.let(::formatSampleRate) ?: "—"
        val precision = (output.outputBits ?: output.transferBits)?.let { "$it bit" } ?: "—"
        val clockLabel = stringResource(if (output.verified) R.string.path_output_clock else R.string.usb_param_transfer_rate)
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth.value / fontScale < 290f) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    UsbOutputMetric(clock, clockLabel)
                    UsbOutputMetric(precision, stringResource(R.string.path_output_bits))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    UsbOutputMetric(clock, clockLabel, Modifier.weight(1f))
                    UsbOutputMetric(precision, stringResource(R.string.path_output_bits), Modifier.weight(1f))
                }
            }
        }
        output.bufferDurationMs?.let {
            Text(stringResource(R.string.usb_param_buffer_capacity, it), style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant)
        }
        }
        val guidance = when {
            !output.connected -> stringResource(R.string.usb_page_connect_note)
            output.verified -> stringResource(R.string.usb_page_verified_note)
            output.streaming && !output.failed -> stringResource(R.string.usb_page_streaming_note)
            else -> stringResource(output.assessment.guidanceResource())
        }
        Text(guidance, style = MaterialTheme.typography.bodyMedium,
            color = if (output.failed) scheme.error else scheme.onSurfaceVariant)
    }
    }
}

@Composable
private fun UsbConnectionSteps(output: UsbOutputPresentation) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val steps: @Composable () -> Unit = {
            UsbConnectionStep(stringResource(R.string.usb_page_step_device), output.connected)
            UsbConnectionStep(stringResource(R.string.usb_page_step_permission), output.permissionGranted)
            UsbConnectionStep(stringResource(R.string.usb_page_step_output), output.streaming)
        }
        if (maxWidth.value / fontScale < 250f) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { steps() }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { steps() }
        }
    }
}

@Composable
private fun UsbConnectionStep(label: String, complete: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).background(
            if (complete) scheme.onSurface else scheme.onSurface.copy(alpha = 0.18f), CircleShape))
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = if (complete) scheme.onSurface else scheme.onSurfaceVariant,
            fontWeight = if (complete) FontWeight.Medium else FontWeight.Normal)
        Text(stringResource(if (complete) R.string.usb_page_step_done else R.string.usb_page_step_pending),
            style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun UsbOutputMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.widthIn(min = 0.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(value, style = MaterialTheme.typography.headlineLarge, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
