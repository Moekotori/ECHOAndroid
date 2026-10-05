package app.echo.android.feature.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.echo.android.design.formatDuration
import app.echo.android.design.progressFraction
import app.echo.android.model.radio.EchoRadioStation

/** Keep seek previews local; the player's position remains the source of truth. */
@Composable
internal fun LyricsScrubber(
    trackKey: String?,
    positionMsState: State<Long>,
    durationMsState: State<Long>,
    onSeek: (Long) -> Unit,
) {
    if (EchoRadioStation.isRadio(trackKey)) {
        RadioPlaybackProgress()
        return
    }
    val position = positionMsState.value
    val duration = durationMsState.value
    var preview by remember(trackKey, duration) { mutableStateOf<Float?>(null) }
    val fraction = preview ?: progressFraction(position, duration)
    val shownPosition = if (duration > 0L) (fraction * duration).toLong() else position
    Column(Modifier.fillMaxWidth()) {
        LinearPlayerSeekBar(
            trackKey = trackKey,
            fraction = fraction,
            engaged = preview != null,
            enabled = duration > 0L,
            onPreview = { preview = it },
            onCommit = { target ->
                if (duration > 0L) onSeek((target * duration).toLong())
                preview = null
            },
            onCancel = { preview = null },
            restingThickness = 2.dp,
            engagedThickness = 4.dp,
        )
        val timeStyle = MaterialTheme.typography.labelSmall.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum", letterSpacing = 0.sp,
        )
        val timeColor = OnArtMuted.copy(alpha = 0.76f)
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(shownPosition), color = timeColor, style = timeStyle)
            Text(formatDuration(duration), color = timeColor, style = timeStyle)
        }
    }
}
