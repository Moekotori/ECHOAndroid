package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import app.echo.android.model.playback.*

@Composable
internal fun SignalCompressorControl(settings: EchoDspSettings, enabled: Boolean, onChange: (EchoDspSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.compressor_title), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.compressor_detail), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        DspChoices(listOf(stringResource(R.string.compressor_off), stringResource(R.string.compressor_gentle),
            stringResource(R.string.compressor_night)), settings.compressor.ordinal, enabled) {
            onChange(settings.copy(compressor = EchoCompressorMode.entries[it]))
        }
    }
}
