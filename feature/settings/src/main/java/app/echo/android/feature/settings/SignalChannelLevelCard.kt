package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.EchoChannelBalance

@Composable
internal fun ChannelLevelCard(
    label: String,
    value: Float,
    output: String,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        SignalNumericValue(label, value, EchoChannelBalance.MinGainDb..EchoChannelBalance.MaxGainDb, enabled,
            { onValueChange(snapEqGain(it)) }, valueLabel = formatEqGain(value))
        ChannelValueSlider(label = label, value = value, onValueChange = { onValueChange(snapEqGain(it)) },
            valueRange = EchoChannelBalance.MinGainDb..EchoChannelBalance.MaxGainDb,
            enabled = enabled, showReadout = false)
        Text(stringResource(R.string.channel_estimated_gain, output),
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}
