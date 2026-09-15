package app.echo.android

import android.app.Application
import androidx.media3.common.util.UnstableApi
import app.echo.android.data.EchoLibraryDatabase
import app.echo.android.data.EchoSettingsStore
import app.echo.android.data.fetchSubsonicLyricsText
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
internal class EchoNowPlayingLyricsSession(private val application: Application) {
    private val database = EchoLibraryDatabase.create(application)
    val controller = LyricsController(
        trackForLyrics = { id -> withContext(Dispatchers.IO) { database.trackDao().getTrackById(id) } },
        lyricsResolver = LocalLyricsResolver(application),
        onlineLyricsResolver = OnlineLyricsResolver(),
        importedLyricsStore = ImportedLyricsStore(application),
        scope = EchoPlaybackProcessRuntime.scope,
        subsonicLyricsLoader = { track ->
            val endpoint = EchoSubsonicEndpointRef.get() ?: return@LyricsController null
            val songId = subsonicSongIdFromTrack(track.id, track.source) ?: return@LyricsController null
            val text = try {
                fetchSubsonicLyricsText(endpoint, songId, track.artist, track.title)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }?.takeIf { it.isNotBlank() } ?: return@LyricsController null
            EchoLyricsParser.parse(text, sourceLabel = "Navidrome").takeIf { it.lines.isNotEmpty() }
        },
    )

    fun start(settingsStore: EchoSettingsStore) {
        EchoPlaybackProcessRuntime.scope.launch {
            settingsStore.appSettings
                .map { it.onlineLyricsEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
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
                val document = (state as? EchoLyricsLoadState.Ready)?.lyrics
                    ?.let { lyrics ->
                        EchoNotificationLyricPolicy.document(
                            trackId,
                            LyricsLineAtPosition.notificationLines(lyrics),
                        )
                    }
                EchoPlaybackProcessRuntime.setNotificationLyrics(document)
            }
        }
    }
}
