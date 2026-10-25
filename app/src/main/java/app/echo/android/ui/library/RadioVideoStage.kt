package app.echo.android.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Optional video owns no audio output and is released whenever its visible stage leaves. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun RadioVideoStage(url: String) {
    val context = LocalContext.current
    var player by remember(url) { mutableStateOf<ExoPlayer?>(null) }
    LifecycleStartEffect(url) {
        val video = ExoPlayer.Builder(context.applicationContext).build().apply {
            volume = 0f
            trackSelectionParameters = TrackSelectionParameters.Builder(context.applicationContext)
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).setMaxVideoSize(1280, 720).build()
            setMediaItem(MediaItem.fromUri(url)); prepare(); play()
        }
        player = video
        onStopOrDispose { player = null; video.release() }
    }
    AndroidView(factory = { PlayerView(it).apply { useController = false } },
        update = { it.player = player }, onRelease = { it.player = null },
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
}
