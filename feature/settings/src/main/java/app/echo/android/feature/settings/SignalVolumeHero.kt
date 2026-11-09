package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoDropdownMenu
import app.echo.android.design.EchoDropdownMenuItem
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoSlider
import app.echo.android.model.playback.EchoVolumeSettings
import kotlin.math.round

private val LevelRange = -60f..0f
private val LevelMarks = listOf("−60", "−45", "−30", "−15", "0")

/** The main fader previews the selected dB value and commits on release. */
@Composable
internal fun SignalVolumeHero(
    volume: EchoVolumeSettings,
    enabled: Boolean,
    deviceLabel: String,
    onVolume: (EchoVolumeSettings) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(R.string.volume_level)
    var draft by remember { mutableFloatStateOf(volume.levelDb.coerceIn(LevelRange)) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(volume.levelDb, enabled) {
        if (!enabled) dragging = false
        if (!dragging) draft = volume.levelDb.coerceIn(LevelRange)
    }
    val commit = rememberUpdatedState { level: Float -> onVolume(volume.copy(levelDb = level)) }

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EchoIcon(Icons.Rounded.Headphones, null, Modifier.size(18.dp))
            Text(deviceLabel, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!enabled) {
            SignalNote(stringResource(R.string.volume_bypassed), error = true)
        }
        val readout = formatEqGain(draft)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (volume.muted && enabled) SignalNote(stringResource(R.string.volume_muted))
            }
            SignalNumericValue(
                label, draft, LevelRange, enabled,
                onValueChange = { draft = it; commit.value(it) },
                valueLabel = readout,
                trigger = { open ->
                    TextButton(
                        onClick = open,
                        enabled = enabled,
                        modifier = Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = label
                            stateDescription = readout
                        },
                        contentPadding = PaddingValues(horizontal = 4.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = scheme.onSurface),
                    ) {
                        Text(
                            readout,
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        EchoIcon(Icons.Rounded.Edit, null, Modifier.padding(start = 8.dp).size(16.dp))
                    }
                },
            )
        }
        EchoSlider(
            value = draft,
            onValueChange = {
                dragging = true
                draft = (round(it * 10f) / 10f).coerceIn(LevelRange)
            },
            onValueChangeFinished = {
                dragging = false
                if (enabled) commit.value(draft)
            },
            valueRange = LevelRange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = label
                stateDescription = readout
            },
        )
        Row(
            Modifier.fillMaxWidth().clearAndSetSemantics { },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            LevelMarks.forEach { mark ->
                Column(Modifier.widthIn(min = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(1.dp).height(4.dp).background(scheme.outlineVariant))
                    Text(mark, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                        color = scheme.onSurfaceVariant)
                }
            }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onVolume(volume.adjusted(-1)) },
                    enabled = enabled && volume.levelDb > LevelRange.start,
                ) {
                    EchoIcon(Icons.Rounded.Remove, stringResource(R.string.volume_decrease))
                }
                VolumeStepPicker(volume.stepDb, enabled) { onVolume(volume.copy(stepDb = it)) }
                IconButton(
                    onClick = { onVolume(volume.adjusted(1)) },
                    enabled = enabled && volume.levelDb < LevelRange.endInclusive,
                ) {
                    EchoIcon(Icons.Rounded.Add, stringResource(R.string.volume_increase))
                }
            }
            TextButton(
                onClick = { onVolume(volume.copy(muted = !volume.muted)) },
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = scheme.onSurface),
            ) {
                val icon = if (volume.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp
                EchoIcon(icon, null, Modifier.padding(end = 6.dp).size(18.dp))
                Text(stringResource(if (volume.muted) R.string.volume_unmute_action else R.string.volume_mute_action))
            }
        }
    }
}

@Composable
private fun VolumeStepPicker(step: Float, enabled: Boolean, onSelect: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(R.string.volume_step)
    val selected = stringResource(R.string.volume_step_value, signalNumber(step))
    Box {
        TextButton(
            onClick = { expanded = true },
            enabled = enabled,
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
        EchoDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
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
