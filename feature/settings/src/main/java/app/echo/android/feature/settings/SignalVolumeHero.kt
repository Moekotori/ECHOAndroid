package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.EchoIcon
import app.echo.android.design.EchoSlider
import app.echo.android.design.echoClickable
import app.echo.android.model.playback.EchoVolumeSettings
import kotlin.math.round

private val LevelRange = -60f..0f

/** Headline card: output device, mute, a large editable dB readout and a stepper-assisted slider. */
@Composable
internal fun SignalVolumeHero(
    volume: EchoVolumeSettings,
    enabled: Boolean,
    deviceLabel: String,
    deviceSummary: String,
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
    val dimmed = volume.muted || !enabled
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.background(Brush.verticalGradient(listOf(scheme.primary.copy(alpha = 0.12f), scheme.primary.copy(alpha = 0f))))
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(scheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                    EchoIcon(Icons.Rounded.Headphones, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f)) {
                    Text(deviceLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(deviceSummary, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                MuteToggle(volume.muted, enabled) { onVolume(volume.copy(muted = it)) }
            }
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                SignalNumericValue(
                    label, draft, LevelRange, enabled, { draft = it; commit.value(it) },
                    valueLabel = formatEqGain(draft),
                    trigger = { open ->
                        val readout = signalNumber(draft)
                        Row(
                            Modifier.heightIn(min = 72.dp).clip(RoundedCornerShape(16.dp))
                                .semantics { contentDescription = label; stateDescription = formatEqGain(draft) }
                                .echoClickable(enabled = enabled, role = Role.Button, onClick = open)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(readout, style = MaterialTheme.typography.displayMedium.copy(fontSize = 60.sp, lineHeight = 68.sp,
                                fontFeatureSettings = "tnum"), fontWeight = FontWeight.Light,
                                color = if (dimmed) scheme.onSurface.copy(alpha = 0.38f) else scheme.onSurface)
                            Text("dB", Modifier.padding(start = 6.dp, bottom = 14.dp), style = MaterialTheme.typography.titleMedium,
                                color = scheme.onSurfaceVariant)
                        }
                    },
                )
                Text(if (volume.muted) stringResource(R.string.volume_muted) else " ",
                    style = MaterialTheme.typography.labelMedium, color = scheme.primary)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StepButton(Icons.Rounded.Remove, stringResource(R.string.volume_decrease), enabled && draft > LevelRange.start) {
                    onVolume(volume.copy(levelDb = (volume.levelDb - volume.stepDb).coerceIn(LevelRange)))
                }
                EchoSlider(
                    value = draft,
                    onValueChange = { dragging = true; draft = (round(it * 10f) / 10f).coerceIn(LevelRange) },
                    onValueChangeFinished = { dragging = false; if (enabled) commit.value(draft) },
                    valueRange = LevelRange, enabled = enabled, neutralValue = 0f,
                    modifier = Modifier.weight(1f).semantics { contentDescription = label; stateDescription = formatEqGain(draft) },
                )
                StepButton(Icons.Rounded.Add, stringResource(R.string.volume_increase), enabled && draft < LevelRange.endInclusive) {
                    onVolume(volume.copy(levelDb = (volume.levelDb + volume.stepDb).coerceIn(LevelRange)))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 52.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatEqGain(LevelRange.start), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                Text(formatEqGain(LevelRange.endInclusive), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MuteToggle(muted: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val label = stringResource(R.string.volume_mute)
    Surface(
        shape = CircleShape,
        color = if (muted) scheme.primary else scheme.surfaceContainerHighest,
        contentColor = if (muted) scheme.onPrimary else scheme.onSurface,
        modifier = Modifier.size(48.dp).semantics { contentDescription = label }
            .toggleable(muted, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EchoIcon(if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, null,
                Modifier.size(22.dp), tint = when {
                    !enabled -> scheme.onSurface.copy(alpha = 0.38f)
                    muted -> scheme.onPrimary
                    else -> scheme.onSurface
                })
        }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(44.dp)) {
        EchoIcon(icon, description, Modifier.size(20.dp))
    }
}
