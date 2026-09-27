package app.echo.android.feature.connect

import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.echo.android.connect.EchoLinkRemoteControlHold
import app.echo.android.design.LocalEchoEffectivePerformanceMode
import app.echo.android.design.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Keep progress ticks inside this small subtree, away from artwork and the song list. */
@Composable
internal fun RemoteSeekControl(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    enabled: Boolean,
    active: Boolean,
    onSeek: (Long) -> Unit,
) {
    val duration = durationMs.coerceAtLeast(0L)
    var dragging by remember { mutableStateOf<Long?>(null) }
    var committed by remember { mutableStateOf<Long?>(null) }
    var committedAt by remember { mutableStateOf<Long?>(null) }
    val anchor = remember(positionMs, isPlaying, active) { SystemClock.elapsedRealtime() }
    var now by remember { mutableLongStateOf(anchor) }
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    LaunchedEffect(anchor, active, isPlaying, committedAt, lightweight) {
        now = SystemClock.elapsedRealtime()
        while (active && (isPlaying || committed != null)) {
            delay(if (lightweight) 1_000L else 400L)
            now = SystemClock.elapsedRealtime()
            if (!EchoLinkRemoteControlHold.shouldHoldCommittedPosition(committed, committedAt, positionMs, now)) {
                committed = null
                committedAt = null
            }
        }
    }
    val live = positionMs + if (isPlaying && active) (now - anchor).coerceAtLeast(0L) else 0L
    val shown = EchoLinkRemoteControlHold.displayedPositionMs(positionMs, live, committed, committedAt, now, dragging)
        .coerceIn(0L, duration)
    val label = stringResource(R.string.remote_seek)
    Column {
        Slider(
            value = shown.toFloat(),
            onValueChange = { dragging = it.toLong() },
            onValueChangeFinished = {
                dragging?.let { target ->
                    committed = target
                    committedAt = SystemClock.elapsedRealtime()
                    onSeek(target)
                }
                dragging = null
            },
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            enabled = enabled && duration > 0L,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(shown), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun RemoteVolumeControl(volume: Float, enabled: Boolean, onVolume: (Float) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    var committed by remember { mutableStateOf<Float?>(null) }
    var committedAt by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(volume, committedAt) {
        if (EchoLinkRemoteControlHold.shouldHoldCommittedVolume(committed, committedAt, volume, SystemClock.elapsedRealtime())) {
            val remaining = EchoLinkRemoteControlHold.HoldTimeoutMs - (SystemClock.elapsedRealtime() - (committedAt ?: 0L))
            delay(remaining.coerceAtLeast(0L))
        }
        committed = null
        committedAt = null
    }
    val shown = (dragging ?: committed ?: volume).coerceIn(0f, 1f)
    val label = stringResource(R.string.feature_connect_volume_8f3a19)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.VolumeUp, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(shown, { dragging = it }, Modifier.weight(1f).semantics { contentDescription = label },
            enabled = enabled,
            onValueChangeFinished = {
                dragging?.let {
                    committed = it
                    committedAt = SystemClock.elapsedRealtime()
                    onVolume(it)
                }
                dragging = null
            })
        Text("${(shown * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.widthIn(min = 36.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
