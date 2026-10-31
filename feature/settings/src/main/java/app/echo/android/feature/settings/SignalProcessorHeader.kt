package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoSwitch

/** The page owns its title; processing state and the master switch share one quiet row. */
@Composable
internal fun SignalProcessorHeader(
    label: String,
    status: String,
    live: Boolean,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    detail: String? = null,
    warning: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SignalLiveDot(live)
                Text(status, style = MaterialTheme.typography.bodyMedium,
                    color = if (warning) scheme.error else scheme.onSurfaceVariant)
            }
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        EchoSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label })
    }
}
