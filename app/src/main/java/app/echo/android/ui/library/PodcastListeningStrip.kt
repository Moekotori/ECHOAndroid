package app.echo.android.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.EchoAndroidViewModel
import app.echo.android.design.EchoIcon
import app.echo.android.feature.podcasts.R

/** Only this strip observes position ticks; subscription and episode rows do not. */
@Composable
internal fun PodcastListeningStrip(viewModel: EchoAndroidViewModel) {
    val metadata by viewModel.playbackMetadata.collectAsStateWithLifecycle()
    if (metadata.mediaId?.startsWith("podcast:") != true) return
    val id = metadata.mediaId
    val controls by viewModel.playbackControls.collectAsStateWithLifecycle()
    val position by viewModel.playbackPosition.collectAsStateWithLifecycle()
    Surface(tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Text(metadata.title, maxLines = 1, style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                IconButton(enabled = controls.canSeek, onClick = {
                    if (viewModel.playbackMetadata.value.mediaId == id) viewModel.seekTo((viewModel.currentPlaybackPositionMs() - 15_000).coerceAtLeast(0))
                }) { EchoIcon(Icons.Rounded.Replay, stringResource(R.string.podcast_rewind)) }
                IconButton(onClick = { if (viewModel.playbackMetadata.value.mediaId == id) viewModel.playPause() }) {
                    EchoIcon(if (controls.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (controls.isPlaying) R.string.podcast_pause else R.string.podcast_play))
                }
                IconButton(enabled = controls.canSeek, onClick = {
                    if (viewModel.playbackMetadata.value.mediaId == id) {
                        val duration = viewModel.playbackPosition.value.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
                        viewModel.seekTo((viewModel.currentPlaybackPositionMs() + 30_000).coerceAtMost(duration))
                    }
                }) { EchoIcon(Icons.Rounded.Forward30, stringResource(R.string.podcast_forward)) }
                Text(stringResource(R.string.podcast_position, podcastTime(position.positionMs), podcastTime(position.durationMs)), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun podcastTime(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)
