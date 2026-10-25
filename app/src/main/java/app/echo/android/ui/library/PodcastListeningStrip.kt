package app.echo.android.ui.library

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.echo.android.EchoAndroidViewModel
import app.echo.android.feature.podcasts.PodcastPlayerBar

/** Only this small boundary collects position ticks. */
@Composable
internal fun PodcastListeningStrip(viewModel: EchoAndroidViewModel) {
    val metadata by viewModel.playbackMetadata.collectAsStateWithLifecycle()
    val id = metadata.mediaId?.takeIf { it.startsWith("podcast:") } ?: return
    val controls by viewModel.playbackControls.collectAsStateWithLifecycle()
    val position by viewModel.playbackPosition.collectAsStateWithLifecycle()
    fun seek(target: Long) {
        if (viewModel.playbackMetadata.value.mediaId != id) return
        val duration = viewModel.playbackPosition.value.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
        viewModel.seekTo(target.coerceIn(0, duration))
    }
    PodcastPlayerBar(id, metadata.title, metadata.artworkUri, position.positionMs, position.durationMs,
        controls.isPlaying, controls.canSeek,
        onToggle = { if (viewModel.playbackMetadata.value.mediaId == id) viewModel.playPause() },
        onJump = { offset -> seek(viewModel.currentPlaybackPositionMs() + offset) }, onSeek = ::seek)
}
