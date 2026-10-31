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
    val scheme = MaterialTheme.colorScheme
    val title = stringResource(R.string.output_audio_profile_title)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            SignalHelpButton(title, stringResource(R.string.output_audio_profile_hint))
        }
        Text(deviceName.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        if (bypassed) SignalNote(stringResource(R.string.output_audio_profile_bypassed), error = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onSave, enabled = !deviceName.isNullOrBlank() && !bypassed,
                contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(if (bound) R.string.output_audio_profile_update else R.string.output_audio_profile_save))
            }
            if (bound) TextButton(onClick = onRemove, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(R.string.output_audio_profile_remove))
            }
        }
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.55f))
    }
}
