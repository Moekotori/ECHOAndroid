package app.echo.android

import android.app.Application
import androidx.media3.common.util.UnstableApi
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.EchoSettingsStore
import app.echo.android.data.fetchSubsonicLyrics
import app.echo.android.data.subsonicSongIdFromTrack
import app.echo.android.lyrics.EchoLyricsParser
import app.echo.android.lyrics.ImportedLyricsStore
import app.echo.android.lyrics.LocalLyricsResolver
import app.echo.android.lyrics.LyricsLineAtPosition
import app.echo.android.lyrics.OnlineLyricsResolver
import app.echo.android.model.lyrics.EchoLyricsLoadState
import app.echo.android.playback.EchoNotificationLyricPolicy
import app.echo.android.playback.EchoPlaybackProcessRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** Process-lifetime lyrics so notification lines survive after the activity is gone. */
@UnstableApi
internal class EchoNowPlayingLyricsSession(application: Application) {
    private val database = EchoLibraryDatabase.create(application)
    private val lyricsIndex = app.echo.android.data.LibraryLyricsIndex(database)
    val controller = LyricsController(
        trackForLyrics = { id -> withContext(Dispatchers.IO) { if (app.echo.android.data.FilePlaylistStore.ownsTrack(id)) app.echo.android.data.FilePlaylistStore(application, database).track(id) else database.trackDao().getTrackById(id) } },
        lyricsResolver = LocalLyricsResolver(application),
        onlineLyricsResolver = OnlineLyricsResolver(),
        importedLyricsStore = ImportedLyricsStore(application),
        scope = EchoPlaybackProcessRuntime.scope,
        onSelectionChanged = lyricsIndex::selectionChanged,
        onLoaded = lyricsIndex::loaded,
        subsonicLyricsLoader = { track ->
            val endpoint = subsonicEndpointForTrack(track.id) ?: return@LyricsController null
            val songId = subsonicSongIdFromTrack(track.id, track.source) ?: return@LyricsController null
            if (track.source != endpoint.sourceId) return@LyricsController null
            val content = try {
                fetchSubsonicLyrics(endpoint, songId, track.artist, track.title,
                    cancellationJob = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job])
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            } ?: return@LyricsController null
            content.document ?: content.text?.let { EchoLyricsParser.parse(it, sourceLabel = "Navidrome") }
                ?.takeIf { it.lines.isNotEmpty() }
        },
    )

    fun start(settingsStore: EchoSettingsStore) {
        EchoPlaybackProcessRuntime.scope.launch {
            var previous = emptyList<app.echo.android.data.SubsonicEndpoint>()
            EchoSubsonicEndpoints.collect { endpoints ->
                val trackId = EchoPlaybackProcessRuntime.surfaceSnapshot.mediaId
                val before = subsonicEndpointForTrack(trackId, previous)
                val ready = subsonicEndpointForTrack(trackId, endpoints)
                previous = endpoints
                if (ready != null && ready != before) controller.updateLyricsForTrack(trackId, force = true)
            }
        }
        EchoPlaybackProcessRuntime.scope.launch {
            settingsStore.appSettings
                .map { it.onlineLyricsEnabled to it.lyricsOptions.sourceOrder }
                .distinctUntilChanged()
                .collect { (enabled, sourceOrder) ->
                    controller.setSourceOrder(sourceOrder, EchoPlaybackProcessRuntime.surfaceSnapshot.mediaId)
                    controller.setOnlineLyricsEnabled(
                        enabled,
                        EchoPlaybackProcessRuntime.surfaceSnapshot.mediaId,
                    )
                }
        }
        EchoPlaybackProcessRuntime.scope.launch {
            EchoPlaybackProcessRuntime.surface
                .map { it.mediaId }
                .distinctUntilChanged()
                .collect { trackId -> controller.updateLyricsForTrack(trackId) }
        }
        EchoPlaybackProcessRuntime.scope.launch {
            controller.lyricsState.collect { state ->
                val trackId = controller.currentTrackId
                val lyrics = (state as? EchoLyricsLoadState.Ready)?.lyrics
                val document = lyrics?.let {
                    EchoNotificationLyricPolicy.document(
                        trackId,
                        LyricsLineAtPosition.notificationLines(it),
                    )
                }
                EchoPlaybackProcessRuntime.setDisplayLyrics(lyrics)
                EchoPlaybackProcessRuntime.setNotificationLyrics(document)
            }
        }
    }
}
