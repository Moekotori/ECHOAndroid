package app.echo.android.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoExpand
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoSwitch

@Composable
internal fun SignalDspSection(
    title: String, detail: String, summary: String,
    checked: Boolean, enabled: Boolean, expanded: Boolean,
    onExpand: () -> Unit, onChecked: (Boolean) -> Unit, onReset: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.65f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.weight(1f).heightIn(min = 68.dp).clickable(onClick = onExpand),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(summary, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
                EchoIcon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
            }
            EchoSwitch(checked, onChecked, enabled = enabled, modifier = Modifier.semantics { contentDescription = title })
        }
        EchoExpand(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!checked && enabled) SignalNote(stringResource(R.string.signal_preset_while_off))
                content()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onReset, enabled = enabled, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(stringResource(R.string.signal_reset_section))
                    }
                    SignalHelpButton(title, detail)
                }
            }
        }
    }
}
