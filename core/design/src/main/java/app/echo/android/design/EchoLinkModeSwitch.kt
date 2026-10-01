package app.echo.android.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Shared by connection UI and the player shell; selection describes the playback destination. */
@Composable
fun EchoLinkModeSwitch(
    remoteMode: Boolean,
    onRemoteModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showDescription: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier) {
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            listOf(false, true).forEach { remote ->
                val selected = remoteMode == remote
                Column(
                    Modifier.weight(1f).selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onRemoteModeChange(remote) },
                    ),
                ) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        EchoIcon(if (remote) Icons.Rounded.Computer else Icons.Rounded.Smartphone,
                            null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                        Text(stringResource(if (remote) R.string.echo_link_mode_remote else R.string.echo_link_mode_normal),
                            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                    HorizontalDivider(thickness = 2.dp, color = if (selected) scheme.onSurface else Color.Transparent)
                }
            }
        }
        if (showDescription) Text(
            stringResource(if (remoteMode) R.string.echo_link_mode_remote_hint else R.string.echo_link_mode_normal_hint),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}
