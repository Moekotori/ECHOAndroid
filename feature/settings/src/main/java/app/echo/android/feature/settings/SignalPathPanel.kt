package app.echo.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.echo.android.model.playback.*

@Composable
internal fun SignalPathPanel(
    status: EchoPlaybackStatus,
    equalizer: EchoEqualizerState,
    balance: EchoChannelBalanceState,
    dsp: EchoDspSettings,
) {
    val d = status.diagnostics
    val scheme = MaterialTheme.colorScheme
    var expanded by rememberSaveable { mutableStateOf(false) }
    val live = status.isPlaying && status.state != EchoPlaybackState.Error
    // A route or a setting alone cannot verify bit-perfect transport.
    val verified = live && d.usbExclusiveStreaming && d.bitPerfectState == EchoBitPerfectState.Direct
    val label = when {
        !live -> playbackStateLabel(status.state)
        verified -> stringResource(R.string.path_verified)
        else -> stringResource(R.string.path_unverified)
    }
    val accent = when {
        status.state == EchoPlaybackState.Error -> scheme.error
        verified -> scheme.primary
        else -> scheme.onSurfaceVariant
    }
    val explanation = when {
        status.state == EchoPlaybackState.Error -> stringResource(R.string.path_error_detail)
        !live -> stringResource(R.string.path_pending_detail)
        d.usbBitPerfectEnabled -> d.bitPerfectReadout(equalizer)
        else -> stringResource(R.string.path_unverified_detail)
    }
    val stages = signalPathStages(status, equalizer, balance, dsp)
    SignalSection(
        title = stringResource(R.string.feature_settings_signal_path_2fed34),
        subtitle = stringResource(R.string.path_journey),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(6.dp).background(accent, CircleShape))
            Text(label, color = accent, style = MaterialTheme.typography.labelMedium)
        }
        SignalNote(explanation)
        val detailState = stringResource(if (expanded) R.string.path_hide_details else R.string.path_show_details)
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.semantics { stateDescription = detailState },
            contentPadding = PaddingValues(horizontal = 0.dp),
        ) {
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(detailState)
        }
        Column {
            stages.forEachIndexed { index, stage ->
                SignalPathStep(stage, index, last = index == stages.lastIndex, expanded = expanded)
            }
        }
    }
}
