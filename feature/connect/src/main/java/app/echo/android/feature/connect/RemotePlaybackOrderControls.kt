package app.echo.android.feature.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.echo.android.design.EchoIcon
import app.echo.android.model.connect.EchoRemotePlaybackOrder

@Composable
internal fun RemotePlaybackOrderButton(
    order: EchoRemotePlaybackOrder,
    enabled: Boolean,
    onChange: (EchoRemotePlaybackOrder) -> Unit,
) {
    IconButton(
        onClick = { onChange(EchoRemotePlaybackOrder.entries[(order.ordinal + 1) % EchoRemotePlaybackOrder.entries.size]) },
        enabled = enabled,
    ) {
        EchoIcon(when (order) {
            EchoRemotePlaybackOrder.Sequential -> Icons.AutoMirrored.Rounded.TrendingFlat
            EchoRemotePlaybackOrder.Shuffle -> Icons.Rounded.Shuffle
            EchoRemotePlaybackOrder.RepeatOne -> Icons.Rounded.RepeatOne
        }, stringResource(order.labelResource()))
    }
}

@Composable
internal fun RemotePlaybackOrderControls(
    order: EchoRemotePlaybackOrder?,
    enabled: Boolean,
    onChange: (EchoRemotePlaybackOrder) -> Unit,
) {
    if (order == null) {
        Text(stringResource(R.string.remote_playback_order_update_pc),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        EchoRemotePlaybackOrder.entries.forEach { option ->
            TextButton(onClick = { onChange(option) }, enabled = enabled && option != order,
                modifier = Modifier.weight(1f).semantics { selected = option == order }) {
                Text(stringResource(option.labelResource()), maxLines = 1,
                    fontWeight = if (option == order) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

private fun EchoRemotePlaybackOrder.labelResource(): Int = when (this) {
    EchoRemotePlaybackOrder.Sequential -> R.string.remote_playback_order_sequential
    EchoRemotePlaybackOrder.Shuffle -> R.string.remote_playback_order_shuffle
    EchoRemotePlaybackOrder.RepeatOne -> R.string.remote_playback_order_repeat_one
}
