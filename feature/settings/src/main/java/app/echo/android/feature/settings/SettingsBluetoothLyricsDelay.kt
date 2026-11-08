package app.echo.android.feature.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.echo.android.model.settings.EchoLyricsOptions
import kotlin.math.roundToInt

@Composable
internal fun SettingsBluetoothLyricsDelay(options: EchoLyricsOptions, onChange: (EchoLyricsOptions) -> Unit) {
    SettingsSliderRow(
        title = stringResource(R.string.settings_bluetooth_lyrics_delay),
        valueLabel = { stringResource(R.string.settings_bluetooth_lyrics_delay_value, it.roundToInt()) },
        value = options.bluetoothDelayMs.toFloat(), valueRange = 0f..2_000f, steps = 39,
        neutralValue = 0f,
        onValueChange = { onChange(options.copy(bluetoothDelayMs = (it / 50f).roundToInt() * 50)) },
    )
    Text(stringResource(R.string.settings_bluetooth_lyrics_delay_hint),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
