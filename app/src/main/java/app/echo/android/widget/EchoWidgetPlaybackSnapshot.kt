package app.echo.android.widget

import android.content.Context
import app.echo.android.data.EchoSavedPlaybackSession
import app.echo.android.data.EchoSettingsStore
import app.echo.android.playback.EchoPlaybackProcessRuntime
import app.echo.android.playback.EchoPlaybackSurfaceSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** A passive read: no MediaController, engine restore, queue copy, or new persistence format. */
internal suspend fun readWidgetPlaybackSnapshot(context: Context): EchoPlaybackSurfaceSnapshot {
    val live = EchoPlaybackProcessRuntime.surfaceSnapshot
    if (live.hasTrack) return live
    val saved = try {
        withTimeoutOrNull(1_500) {
            withContext(Dispatchers.IO) { EchoSettingsStore(context.applicationContext).getSavedPlaybackSession() }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) { null }
    // Playback may have started while the store was read.
    return widgetPlaybackSnapshot(EchoPlaybackProcessRuntime.surfaceSnapshot, saved)
}

internal fun widgetPlaybackSnapshot(
    live: EchoPlaybackSurfaceSnapshot,
    saved: EchoSavedPlaybackSession?,
): EchoPlaybackSurfaceSnapshot {
    if (live.hasTrack) return live
    val track = saved?.queue?.getOrNull(saved.currentIndex) ?: return live
    return EchoPlaybackSurfaceSnapshot(
        title = track.title, artist = track.artist, album = track.album,
        isPlaying = false, hasTrack = true, mediaId = track.id, artworkUri = track.artworkUri,
        playUri = track.uri, positionMs = saved.positionMs.coerceAtLeast(0), durationMs = track.durationMs,
    )
}
