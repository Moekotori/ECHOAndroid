package app.echo.android.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.design.echoClickable

/** Sound tools share a compact index; each processor keeps its existing editor. */
@Composable
internal fun SignalSoundMenu(labels: List<String>, states: List<SignalSoundMenuState>, onSelect: (Int) -> Unit) {
    val descriptions = listOf(
        stringResource(R.string.signal_sound_eq_summary),
        stringResource(R.string.signal_sound_headphones_summary),
        stringResource(R.string.signal_sound_channels_summary),
        stringResource(R.string.signal_sound_dsp_summary),
        stringResource(R.string.signal_sound_mseb_summary),
        stringResource(R.string.volume_summary),
    )
    val icons = listOf(Icons.Rounded.Equalizer, Icons.Rounded.Headphones, Icons.Rounded.SwapHoriz, Icons.Rounded.Tune, Icons.Rounded.Waves, Icons.AutoMirrored.Rounded.VolumeUp)
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        val order = listOf(labels.lastIndex) + (0 until labels.lastIndex)
        order.forEach { index ->
            val label = labels[index]
            val state = states[index]
            val badge = when (state) {
                SignalSoundMenuState.Off -> null
                SignalSoundMenuState.Enabled -> stringResource(R.string.settings_on)
                SignalSoundMenuState.Adjusted -> stringResource(R.string.signal_sound_adjusted)
                SignalSoundMenuState.Bypassed -> stringResource(R.string.signal_sound_bypassed)
            }
            val stateLabel = badge ?: stringResource(R.string.path_disabled)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 76.dp)
                    .semantics { stateDescription = stateLabel }
                    .echoClickable(role = Role.Button, onClick = { onSelect(index) })
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EchoIcon(icons[index], null, Modifier.size(24.dp), tint = scheme.onSurfaceVariant)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        if (badge != null) Surface(
                            shape = RoundedCornerShape(50),
                            color = if (state == SignalSoundMenuState.Bypassed) scheme.surfaceContainerHigh else scheme.secondaryContainer,
                            contentColor = if (state == SignalSoundMenuState.Bypassed) scheme.onSurfaceVariant else scheme.onSecondaryContainer,
                        ) {
                            Text(badge, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(descriptions[index], style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                EchoIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(20.dp))
            }
            if (index != order.last()) HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.55f))
        }
    }
}

@Composable
internal fun SignalSoundToolHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onBack) {
            EchoIcon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.signal_sound_back))
        }
        Text(title, Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    }
}
