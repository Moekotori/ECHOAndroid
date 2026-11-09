package app.echo.android.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.echo.android.design.EchoIcon
import app.echo.android.model.playback.*

@Composable
internal fun SignalPathPanel(
    status: EchoPlaybackStatus,
    equalizer: EchoEqualizerState,
    balance: EchoChannelBalanceState,
    dsp: EchoDspSettings,
    volume: EchoVolumeSettings = EchoVolumeSettings(),
    onDiagnostics: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(false) }
    val tone = status.signalPathTone()
    val hasTrack = status.track != null
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainerLow,
        contentColor = scheme.onSurface,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().then(
                    if (hasTrack) Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(if (expanded) R.string.path_hide_details else R.string.path_show_details),
                        onClick = { expanded = !expanded },
                    ) else Modifier,
                )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SignalPathGlyph(tone, scheme.onSurfaceVariant)
                Text(stringResource(R.string.feature_settings_signal_path_2fed34), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (hasTrack) {
                    EchoIcon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (hasTrack) {
                    val stages = signalPathStages(status, equalizer, balance, dsp, volume)
                    val ink = tone.tint(scheme.onSurfaceVariant)
                    stages.forEachIndexed { index, stage ->
                        SignalPathStep(stage, index, last = index == stages.lastIndex, expanded = expanded, accent = ink)
                    }
                } else {
                    Text(stringResource(R.string.diag_waiting_playback), Modifier.padding(4.dp),
                        style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.6f))
            TextButton(onClick = onDiagnostics, modifier = Modifier.padding(horizontal = 6.dp)) {
                Text(stringResource(R.string.feature_settings_diagnostics_b6e487))
                Spacer(Modifier.width(6.dp))
                EchoIcon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null,
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}
