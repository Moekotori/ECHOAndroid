package app.echo.android.library

import android.content.Intent
import android.provider.MediaStore
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.toEchoTrack
import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibrarySource
import app.echo.android.model.playback.EchoMediaSearchRequest
import app.echo.android.playback.EchoPlaybackProcessRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun Intent.voiceLaunchRequest(): EchoMediaSearchRequest? {
    if (action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return null
    fun hint(key: String) = getStringExtra(key)?.trim()?.take(256)?.takeIf { it.isNotEmpty() }
    return EchoMediaSearchRequest(hint(android.app.SearchManager.QUERY).orEmpty(),
        hint(MediaStore.EXTRA_MEDIA_FOCUS), hint(MediaStore.EXTRA_MEDIA_TITLE), hint(MediaStore.EXTRA_MEDIA_ALBUM),
        hint(MediaStore.EXTRA_MEDIA_ARTIST), hint(MediaStore.EXTRA_MEDIA_PLAYLIST), hint(MediaStore.EXTRA_MEDIA_GENRE))
}

@androidx.media3.common.util.UnstableApi
internal suspend fun resolveVoiceLaunchTracks(database: EchoLibraryDatabase, request: EchoMediaSearchRequest): Pair<List<EchoTrack>, Int> = withContext(Dispatchers.IO) {
    val result = EchoPlaybackProcessRuntime.catalog().voiceQueue(request)
    if (result.items.isEmpty()) return@withContext emptyList<EchoTrack>() to 0
    val entities = database.trackDao().getTracksByIds(result.items.map { it.mediaId }).associateBy { it.id }
    val queue = result.items.mapNotNull { item ->
        entities[item.mediaId]?.toEchoTrack() ?: item.playUri?.let { uri ->
            EchoTrack(item.mediaId, item.persistUri ?: uri, item.title, item.subtitle.orEmpty(),
                artworkUri = item.artworkUri, durationMs = item.durationMs, source = LibrarySource.Unknown,
                clipStartMs = item.clipStartMs, clipEndMs = item.clipEndMs)
        }
    }
    val anchor = result.items.getOrNull(result.startIndex)?.mediaId
    queue to queue.indexOfFirst { it.id == anchor }.coerceAtLeast(0)
}
