package app.echo.android.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.playback.EchoBitPerfectState
import app.echo.android.model.playback.EchoPlaybackStatus

internal const val BIT_PERFECT_LABEL = "Bit-Perfect"

@Composable
internal fun SignalDeviceCard(status: EchoPlaybackStatus) {
    val d = status.diagnostics
    val kind = d.signalOutputKind()
    val route = outputDeviceKindLabel(kind.id)
    val deviceName = d.signalOutputName()
    val bitPerfect = status.signalBitPerfectState()
    val verified = bitPerfect == EchoBitPerfectState.Direct
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceContainer,
        contentColor = scheme.onSurface,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = scheme.surfaceContainerHigh) {
                    EchoIcon(kind.signalOutputIcon(), contentDescription = null,
                        modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.diag_output_end), color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium)
                    Text(deviceName ?: route, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    if (deviceName != null) SignalNote(route)
                }
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (verified) scheme.primaryContainer else scheme.surfaceContainerHigh,
                contentColor = if (verified) scheme.onPrimaryContainer else scheme.onSurface,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EchoIcon(if (verified) Icons.Rounded.Check else Icons.Rounded.Route,
                        contentDescription = null, modifier = Modifier.size(18.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(BIT_PERFECT_LABEL, style = MaterialTheme.typography.labelMedium,
                            fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                        Text(bitPerfect.readout(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
