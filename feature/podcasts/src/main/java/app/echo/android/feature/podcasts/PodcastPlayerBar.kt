package app.echo.android.feature.podcasts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.echo.android.design.*

@Composable
fun PodcastPlayerBar(id: String, title: String, artwork: String?, positionMs: Long, durationMs: Long,
    playing: Boolean, canSeek: Boolean, onToggle: () -> Unit, onJump: (Long) -> Unit, onSeek: (Long) -> Unit) {
    var dragging by remember(id) { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(1).toFloat()
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.padding(vertical = 8.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EchoArtworkImage(artwork, null, Modifier.size(42.dp), sizeClass = EchoArtworkSize.Tiny)
                Text(title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { onJump(-15_000) }, enabled = canSeek) { EchoIcon(Icons.Rounded.Replay, stringResource(R.string.podcast_rewind)) }
                FilledTonalIconButton(onClick = onToggle) {
                    EchoIcon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, stringResource(if (playing) R.string.podcast_pause else R.string.podcast_play))
                }
                IconButton(onClick = { onJump(30_000) }, enabled = canSeek) { EchoIcon(Icons.Rounded.Forward30, stringResource(R.string.podcast_forward)) }
            }
            Slider(value = dragging ?: positionMs.toFloat().coerceIn(0f, duration),
                onValueChange = { dragging = it }, onValueChangeFinished = { dragging?.let { onSeek(it.toLong()) }; dragging = null },
                enabled = canSeek && durationMs > 0, valueRange = 0f..duration, modifier = Modifier.fillMaxWidth().height(28.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(podcastTime(dragging?.toLong() ?: positionMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(podcastTime(durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
