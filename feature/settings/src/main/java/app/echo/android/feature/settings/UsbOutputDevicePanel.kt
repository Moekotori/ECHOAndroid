package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon

@Composable
internal fun UsbOutputDevicePanel(output: UsbOutputPresentation) {
    val scheme = MaterialTheme.colorScheme
    val statusColor = if (output.failed) scheme.error else scheme.onSurfaceVariant
    Column(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EchoIcon(
                SettingsCategoryIcons.Usb, null, Modifier.padding(top = 1.dp).size(22.dp),
                tint = scheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    output.deviceName ?: stringResource(
                        if (output.connected) R.string.diag_usb_recognized else R.string.usb_page_connect_title,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                if (output.connected) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            output.audioClass?.let { "USB DAC · $it" } ?: "USB DAC",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(5.dp).background(statusColor, CircleShape))
                            Text(
                                stringResource(output.headlineResource()),
                                style = MaterialTheme.typography.labelMedium,
                                color = statusColor,
                            )
                        }
                    }
                }
            }
        }
        val clock = (output.clockHz ?: output.transferRateHz)?.let(::formatSampleRate)
        val precision = (output.outputBits ?: output.transferBits)?.let { "$it bit" }
        if (clock != null || precision != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                UsbOutputMetric(
                    clock ?: "—",
                    stringResource(if (output.verified) R.string.path_output_clock else R.string.usb_param_transfer_rate),
                )
                UsbOutputMetric(precision ?: "—", stringResource(R.string.path_output_bits))
            }
        }
        val guidance = when {
            !output.connected -> R.string.usb_page_connect_note
            output.verified -> R.string.usb_page_verified_note
            output.streaming && output.assessment == DacAssessment.Compatible -> R.string.usb_page_compatible_note
            output.streaming && !output.failed -> R.string.usb_page_streaming_note
            else -> output.assessment.guidanceResource()
        }
        SignalNote(stringResource(guidance), error = output.failed)
    }
}

@Composable
private fun UsbOutputMetric(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
        SignalNote(label)
    }
}
