package app.echo.android

import app.echo.android.model.error.EchoErrorLog
import app.echo.android.model.error.EchoErrorSource
import app.echo.android.model.podcast.EchoPodcastEpisode
import app.echo.android.model.podcast.EchoPodcastFeed
import app.echo.android.model.radio.EchoRadioStation
import app.echo.android.radio.EchoRadioController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Wires network sources into the existing queue and replacement ownership boundary. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class BroadcastPlaybackController(
    private val application: EchoApplication,
    private val playback: PlaybackController,
    private val selection: PreferredPlaybackSelection,
    private val radio: EchoRadioController,
) {
    private val mutablePending = MutableStateFlow(false)
    val radioPending = mutablePending.asStateFlow()
    private val mutableFailed = MutableStateFlow(false)
    val radioFailed = mutableFailed.asStateFlow()
    private var generation = 0L
    private var lastRadio: Pair<EchoRadioStation, String?>? = null

    fun playRadio(station: EchoRadioStation, streamUrl: String?) {
        val token = ++generation
        lastRadio = station to streamUrl
        mutablePending.value = true; mutableFailed.value = false
        selection.launch {
            try {
                var track = station.toTrack(streamUrl)
                if (java.net.URI(track.uri).path.orEmpty().matches(Regex("(?i).*\\.(m3u|pls)$"))) {
                    val probe = radio.probe(track.uri)
                    probe.stations.firstOrNull()?.let { resolved -> track = track.copy(uri = resolved.url) }
                    if (probe.codec == "HLS") track = track.copy(mimeType = "application/x-mpegURL")
                }
                application.radioSession.request(station)
                playback.play(track)
            } catch (e: CancellationException) { throw e }
            catch (error: Exception) {
                mutableFailed.value = true
                EchoErrorLog.record(EchoErrorSource.Playback, "Radio stream resolution failed", throwable = error)
            } finally { if (token == generation) mutablePending.value = false }
        }
    }
    fun retryRadio() { lastRadio?.let { playRadio(it.first, it.second) } }

    fun playClipboard(text: String) {
        selection.launch {
            try {
                val entries = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    app.echo.android.data.RadioPlaylistCodec.parse(text)
                }
                val station = entries.firstOrNull() ?: throw IllegalArgumentException("No radio address")
                playRadio(station, null)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableFailed.value = true }
        }
    }

    fun playPodcast(feed: EchoPodcastFeed, episode: EchoPodcastEpisode, restart: Boolean) {
        if (!restart && playback.currentTrackId == episode.id && playback.playbackControls.value.state in
            setOf(app.echo.android.model.playback.EchoPlaybackState.Playing, app.echo.android.model.playback.EchoPlaybackState.Paused)) {
            selection.invalidate(); playback.playPause(); return
        }
        selection.launch {
            val store = application.podcasts.store
            val latest = store.episode(episode.id) ?: episode
            val position = if (restart || latest.played) 0L else latest.positionMs
            if (restart || latest.played) store.progress(episode.id, 0, ended = false, replay = true)
            playback.playAt(latest.toTrack(feed), position)
        }
    }

    fun listenPodcast(feed: EchoPodcastFeed) {
        selection.launch {
            val store = application.podcasts.store
            val active = playback.currentTrackId?.takeIf { playback.playbackControls.value.state in
                setOf(app.echo.android.model.playback.EchoPlaybackState.Playing, app.echo.android.model.playback.EchoPlaybackState.Paused) }
                ?.let { store.episode(it) }?.takeIf { it.feedId == feed.id }
            if (active != null) {
                if (!playback.playbackControls.value.isPlaying) playback.playPause()
            } else store.continueEpisode(feed.id)?.let { episode ->
                val position = if (episode.played) 0L else episode.positionMs
                if (episode.played) store.progress(episode.id, 0, ended = false, replay = true)
                playback.playAt(episode.toTrack(feed), position)
            }
        }
    }

    fun enqueuePodcast(feed: EchoPodcastFeed, episode: EchoPodcastEpisode, next: Boolean) {
        if (next) playback.playNext(episode.toTrack(feed)) else playback.enqueue(episode.toTrack(feed))
    }
}
