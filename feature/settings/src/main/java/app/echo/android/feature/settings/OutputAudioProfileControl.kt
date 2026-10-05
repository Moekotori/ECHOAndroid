package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun OutputAudioProfileControl(deviceName: String?, bound: Boolean, bypassed: Boolean,
    onSave: () -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.output_audio_profile_title), style = MaterialTheme.typography.titleSmall)
        Text(deviceName.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(if (bypassed) R.string.output_audio_profile_bypassed else R.string.output_audio_profile_hint),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSave, enabled = !deviceName.isNullOrBlank() && !bypassed) {
                Text(stringResource(if (bound) R.string.output_audio_profile_update else R.string.output_audio_profile_save))
            }
            if (bound) TextButton(onClick = onRemove) { Text(stringResource(R.string.output_audio_profile_remove)) }
        }
        HorizontalDivider()
    }
}
