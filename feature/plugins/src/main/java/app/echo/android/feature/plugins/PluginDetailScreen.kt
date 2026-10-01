package app.echo.android.feature.plugins

import app.echo.android.design.EchoSwitch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.echo.android.plugin.PluginCapability
import app.echo.android.plugin.PluginSummary

@Composable
internal fun PluginDetailScreen(
    plugin: PluginSummary,
    onBack: () -> Unit,
    onEnable: (Boolean) -> Unit,
    onGrant: (PluginCapability, Boolean) -> Unit,
    onOpenPage: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val enabledLabel = stringResource(R.string.plugins_enabled)
    val canOpen = plugin.enabled && PluginCapability.UiPage in plugin.grants
    PluginChrome(title = plugin.name, onBack = onBack) {
        if (plugin.summary.isNotBlank()) {
            Text(plugin.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            stringResource(R.string.plugins_version, plugin.version),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        plugin.failureText()?.let { failure ->
            Text(failure, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(enabledLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                EchoSwitch(checked = plugin.enabled, onCheckedChange = onEnable,
                    modifier = Modifier.semantics { contentDescription = enabledLabel })
            }
        }
        Text(stringResource(R.string.plugins_permissions), style = MaterialTheme.typography.titleMedium)
        if (plugin.requested.isEmpty()) {
            Text(stringResource(R.string.plugins_permissions_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                plugin.requested.forEach { capability ->
                    val capabilityLabel = capability.title()
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(capabilityLabel, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    capability.detail(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            EchoSwitch(
                                checked = capability in plugin.grants,
                                modifier = Modifier.semantics { contentDescription = capabilityLabel },
                                onCheckedChange = { onGrant(capability, it) },
                            )
                        }
                    }
                }
            }
        }
        Button(onClick = onOpenPage, enabled = canOpen) { Text(stringResource(R.string.plugins_open_page)) }
        if (!canOpen) {
            Text(
                stringResource(R.string.plugins_open_page_blocked),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (plugin.logs.isNotEmpty()) {
            Text(stringResource(R.string.plugins_logs), style = MaterialTheme.typography.titleMedium)
            plugin.logs.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        TextButton(onClick = { confirmDelete = true }) {
            Text(stringResource(R.string.plugins_delete), color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.plugins_delete_title)) },
            text = { Text(stringResource(R.string.plugins_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.plugins_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.plugins_cancel)) }
            },
        )
    }
}
