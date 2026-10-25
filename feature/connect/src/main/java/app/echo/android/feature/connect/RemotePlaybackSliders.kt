package app.echo.android.feature.connect

import app.echo.android.design.EchoSlider
import app.echo.android.design.EchoIcon

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
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
    interpolate: Boolean = true,
) {
    val duration = durationMs.coerceAtLeast(0L)
    var dragging by remember { mutableStateOf<Long?>(null) }
    val anchor = remember(positionMs, isPlaying, active) { SystemClock.elapsedRealtime() }
    var now by remember { mutableLongStateOf(anchor) }
    val lightweight = LocalEchoEffectivePerformanceMode.current.isLightweight
    LaunchedEffect(anchor, active, isPlaying, lightweight, interpolate) {
        now = SystemClock.elapsedRealtime()
        while (active && isPlaying && interpolate) {
            delay(if (lightweight) 1_000L else 100L)
            now = SystemClock.elapsedRealtime()

        }
    }
    val live = positionMs + if (isPlaying && active && interpolate) (now - anchor).coerceAtLeast(0L) else 0L
    val shown = (dragging ?: live)
        .coerceIn(0L, duration)
    val label = stringResource(R.string.remote_seek)
    val smooth by animateFloatAsState(
        targetValue = shown.toFloat(),
        animationSpec = tween(
            durationMillis = if (active && isPlaying && !lightweight && dragging == null) 100 else 0,
            easing = LinearEasing,
        ),
        label = "remoteProgress",
    )
    Column {
        EchoSlider(
            value = if (dragging != null) shown.toFloat() else smooth,
            onValueChange = { dragging = it.toLong() },
            onValueChangeFinished = {
                dragging?.let { target ->
                    onSeek(target)
                }
                dragging = null
            },
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            enabled = enabled && duration > 0L,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(shown), style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun RemoteVolumeControl(volume: Float, enabled: Boolean, onVolume: (Float) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = (dragging ?: volume).coerceIn(0f, 1f)
    val label = stringResource(R.string.feature_connect_volume_8f3a19)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        EchoIcon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        EchoSlider(shown, { dragging = it }, Modifier.weight(1f).semantics { contentDescription = label },
            enabled = enabled,
            onValueChangeFinished = {
                dragging?.let {
                    onVolume(it)
                }
                dragging = null
            })
        Text("${(shown * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.widthIn(min = 36.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
