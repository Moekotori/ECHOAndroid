package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = scheme.onSurface.copy(alpha = 0.035f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            Text(formatEqGain(value), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold, color = scheme.primary)
            Slider(value = value, onValueChange = { onValueChange(snapEqGain(it)) },
                valueRange = EchoChannelBalance.MinGainDb..EchoChannelBalance.MaxGainDb,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
            Text(output, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
    }
}
