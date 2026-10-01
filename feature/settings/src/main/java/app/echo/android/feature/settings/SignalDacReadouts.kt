package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun SignalDacDeviceHeader(device: String, audioClass: String?, headline: String, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Usb, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(device, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).background(accent, CircleShape))
                Text(headline, style = MaterialTheme.typography.labelMedium, color = accent)
            }
        }
        audioClass?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SignalDacOutputMetrics(clock: Pair<String, String>, precision: Pair<String, String>, unknown: String) {
    // Keep enlarged accessibility text and narrow screens from squeezing the two readouts.
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth.value / fontScale < 280f) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DacOutputMetric(clock, unknown)
                DacOutputMetric(precision, unknown)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                DacOutputMetric(clock, unknown, Modifier.weight(1f))
                DacOutputMetric(precision, unknown, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DacOutputMetric(readout: Pair<String, String>, unknown: String, modifier: Modifier = Modifier) {
    val reported = readout.second != unknown
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SelectionContainer {
            Text(readout.second,
                style = if (reported) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                fontFamily = if (reported) FontFamily.Monospace else FontFamily.Default,
                color = if (reported) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(readout.first, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SignalDacAdvice(title: String, detail: String, accent: Color) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(accent.copy(alpha = 0.6f)))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = accent)
            SignalNote(detail)
        }
    }
}
