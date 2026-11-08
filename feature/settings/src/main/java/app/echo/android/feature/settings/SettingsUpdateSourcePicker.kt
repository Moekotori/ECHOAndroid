package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.update.GithubUpdateSource

@Composable
internal fun SettingsUpdateSourcePicker(source: GithubUpdateSource, onSelect: (GithubUpdateSource) -> Unit,
    enabled: Boolean) {
    var expanded by remember(enabled) { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.update_route), style = MaterialTheme.typography.titleSmall)
        Box {
            TextButton(onClick = { expanded = true }, enabled = enabled, shape = SettingsShape,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(updateSourceLabel(source), Modifier.weight(1f))
                EchoIcon(Icons.Default.KeyboardArrowDown, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
                GithubUpdateSource.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(updateSourceLabel(option)) },
                        trailingIcon = {
                            if (source == option) EchoIcon(Icons.Default.Check, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface)
                        },
                        onClick = { expanded = false; onSelect(option) })
                }
            }
        }
        Text(stringResource(R.string.update_route_hint), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun updateSourceLabel(source: GithubUpdateSource): String = stringResource(when (source) {
    GithubUpdateSource.Direct -> R.string.update_route_direct
    GithubUpdateSource.GhFast -> R.string.update_route_ghfast
    GithubUpdateSource.GhProxyNet -> R.string.update_route_ghproxy_net
    GithubUpdateSource.GhProxy -> R.string.update_route_ghproxy
    GithubUpdateSource.GhProxyHk -> R.string.update_route_ghproxy_hk
    GithubUpdateSource.GhProxyCdn -> R.string.update_route_ghproxy_cdn
    GithubUpdateSource.GhProxyEdgeOne -> R.string.update_route_ghproxy_edgeone
})
