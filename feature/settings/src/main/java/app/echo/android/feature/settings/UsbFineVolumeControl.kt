package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoSlider
import app.echo.android.model.playback.EchoUsbFineVolume
import app.echo.android.model.playback.EchoUsbOutputOptions
import kotlin.math.roundToInt

@Composable
internal fun UsbFineVolumeControl(options: EchoUsbOutputOptions, strict: Boolean, onChange: (EchoUsbOutputOptions) -> Unit) {
    var step by remember(options.effectiveVolumeStep, strict) { mutableIntStateOf(if (strict) 300 else options.effectiveVolumeStep) }
    var edited by remember(options.volumePercent, options.volumeStep, strict) { mutableStateOf(false) }
    val displayedDb = if (!strict && !edited && options.volumeStep == null && options.volumeGain > 0f)
        (20.0 * kotlin.math.log10(options.volumeGain.toDouble())).toFloat() else EchoUsbFineVolume.db(step)
    val value = if (step == 0) stringResource(R.string.usb_fine_muted)
        else stringResource(R.string.usb_fine_value, displayedDb)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.usb_fine_title), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        EchoSlider(step.toFloat(), { edited = true; step = it.roundToInt() }, valueRange = 0f..300f, enabled = !strict,
            onValueChangeFinished = { if (!strict) onChange(options.withVolumeStep(step)) })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(enabled = !strict && step > 0, onClick = { edited = true; step--; onChange(options.withVolumeStep(step)) }) {
                Text(stringResource(R.string.usb_fine_down))
            }
            TextButton(enabled = !strict && step < 300, onClick = { edited = true; step++; onChange(options.withVolumeStep(step)) }) {
                Text(stringResource(R.string.usb_fine_up))
            }
        }
        Text(stringResource(if (strict) R.string.usb_param_volume_fixed else R.string.usb_fine_note),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
