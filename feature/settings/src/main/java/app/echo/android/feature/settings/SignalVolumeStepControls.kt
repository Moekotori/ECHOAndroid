package app.echo.android.feature.settings

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoDropdownMenu
import app.echo.android.design.EchoDropdownMenuItem
import app.echo.android.design.EchoIcon
import app.echo.android.design.LocalEchoPageInteractive
import app.echo.android.model.playback.EchoVolumeSettings
import kotlinx.coroutines.delay

private const val StepRepeatDelayMs = 120L

@Composable
internal fun SignalVolumeStepControls(
    volume: EchoVolumeSettings,
    onVolume: (EchoVolumeSettings) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        VolumeStepButton(
            Icons.Rounded.Remove,
            stringResource(R.string.volume_decrease),
            volume.levelDb > AppVolumeLevelRange.start,
        ) { onVolume(volume.adjusted(-1)) }
        VolumeStepPicker(volume.stepDb) { onVolume(volume.copy(stepDb = it)) }
        VolumeStepButton(
            Icons.Rounded.Add,
            stringResource(R.string.volume_increase),
            volume.levelDb < AppVolumeLevelRange.endInclusive,
        ) { onVolume(volume.adjusted(1)) }
    }
}

@Composable
private fun VolumeStepButton(icon: ImageVector, label: String, enabled: Boolean, onAdjust: () -> Unit) {
    val interactive = enabled && LocalEchoPageInteractive.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val adjust by rememberUpdatedState(onAdjust)
    var repeating by remember { mutableStateOf(false) }
    LaunchedEffect(pressed, interactive, repeating) {
        if (!pressed || !interactive) {
            repeating = false
        } else if (repeating) {
            while (pressed) {
                delay(StepRepeatDelayMs)
                if (pressed) adjust()
            }
        }
    }
    Box(
        Modifier.size(48.dp).clip(CircleShape).combinedClickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = interactive,
            role = Role.Button,
            onLongClick = {
                adjust()
                repeating = pressed
            },
            onClick = onAdjust,
        ),
        contentAlignment = Alignment.Center,
    ) {
        val foreground = MaterialTheme.colorScheme.onSurface
        EchoIcon(icon, label, tint = if (enabled) foreground else foreground.copy(alpha = 0.38f))
    }
}

@Composable
private fun VolumeStepPicker(step: Float, onSelect: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(R.string.volume_step)
    val selected = stringResource(R.string.volume_step_value, signalNumber(step))
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.heightIn(min = 48.dp).semantics {
                contentDescription = label
                stateDescription = selected
            },
            contentPadding = PaddingValues(horizontal = 4.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = scheme.onSurface),
        ) {
            Text("${signalNumber(step)} dB", style = MaterialTheme.typography.labelLarge)
            EchoIcon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(16.dp))
        }
        EchoDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            EchoVolumeSettings.Steps.forEach { option ->
                EchoDropdownMenuItem(
                    text = { Text(stringResource(R.string.volume_step_value, signalNumber(option))) },
                    selected = option == step,
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
