package app.echo.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsPermissionsContent(
    state: SettingsPermissionState,
    floatingLyricsEnabled: Boolean,
    onOpenSettings: (SettingsPermissionTarget) -> Unit,
) {
    SettingsSectionCard(stringResource(R.string.permissions_common)) {
        PermissionRow(R.string.permissions_audio, R.string.permissions_audio_detail,
            state.audioGranted, required = true) { onOpenSettings(SettingsPermissionTarget.App) }
        PermissionRow(R.string.permissions_notifications, R.string.permissions_notifications_detail,
            state.notificationsEnabled, required = true) { onOpenSettings(SettingsPermissionTarget.Notifications) }
    }
    SettingsSectionCard(stringResource(R.string.permissions_optional)) {
        PermissionRow(R.string.permissions_overlay, R.string.permissions_overlay_detail,
            state.overlayGranted, required = floatingLyricsEnabled) { onOpenSettings(SettingsPermissionTarget.Overlay) }
        state.bluetoothGranted?.let { granted ->
            PermissionRow(R.string.permissions_bluetooth, R.string.permissions_bluetooth_detail,
                granted, required = false) { onOpenSettings(SettingsPermissionTarget.App) }
        }
        state.localNetworkGranted?.let { granted ->
            PermissionRow(R.string.permissions_network, R.string.permissions_network_detail,
                granted, required = false) { onOpenSettings(SettingsPermissionTarget.App) }
        }
    }
    SettingsSectionCard(stringResource(R.string.permissions_auto_reset)) {
        Text(stringResource(R.string.permissions_auto_reset_detail),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { onOpenSettings(SettingsPermissionTarget.App) }) {
            Text(stringResource(R.string.permissions_app_settings))
        }
    }
}

@Composable
private fun PermissionRow(title: Int, detail: Int, granted: Boolean, required: Boolean, onClick: () -> Unit) {
    val attention = !granted && required
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).settingsSearchAnchor(stringResource(title)),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Text(stringResource(when {
            granted -> R.string.permissions_allowed
            attention -> R.string.permissions_disabled
            else -> R.string.permissions_on_demand
        }), style = MaterialTheme.typography.labelMedium,
            color = if (attention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(detail), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick) { Text(stringResource(R.string.permissions_manage)) }
    }
}
