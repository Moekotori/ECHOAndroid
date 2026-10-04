package app.echo.android.feature.connect

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun PhoneLibraryShareControl(status: String, connected: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(stringResource(R.string.phone_library_share_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.phone_library_share_description),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = status == "starting" || status == "sharing", onCheckedChange = onChange,
                enabled = connected || status == "sharing" || status == "starting")
        }
        Text(stringResource(when {
            status == "unsupported" -> R.string.phone_library_share_unsupported
            status == "error" -> R.string.phone_library_share_error
            status == "starting" -> R.string.phone_library_share_starting
            status == "sharing" -> R.string.phone_library_share_active
            !connected -> R.string.phone_library_share_connect_first
            else -> R.string.phone_library_share_off
        }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
