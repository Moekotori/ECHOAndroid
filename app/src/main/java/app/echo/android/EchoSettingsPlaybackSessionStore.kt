package app.echo.android

import app.echo.android.data.EchoSavedPlaybackSession
import app.echo.android.data.EchoSettingsStore
import app.echo.android.playback.EchoPlaybackSessionSnapshot
import app.echo.android.playback.EchoPlaybackSessionStore
import app.echo.android.playback.EchoPlaybackRuntimeOptionsStore
import kotlinx.coroutines.flow.first
import app.echo.android.playback.withoutHiddenTracks

class EchoSettingsPlaybackSessionStore(
    private val settingsStore: EchoSettingsStore,
    private val hiddenIds: suspend (List<String>) -> Set<String> = { emptySet() },
) : EchoPlaybackSessionStore {
    override suspend fun load(): EchoPlaybackSessionSnapshot? {
        val settings = settingsStore.appSettings.first()
        EchoPlaybackRuntimeOptionsStore.setTrackTransitions(settings.trackTransitions)
        EchoPlaybackRuntimeOptionsStore.setShuffleOptions(settings.shuffleOptions)
        EchoPlaybackRuntimeOptionsStore.setListeningOptions(
            app.echo.android.model.playback.EchoAudioInterruptionPolicy.fromId(settings.audioInterruptionPolicy),
            settings.sleepTimerFinishTrack,
            settings.stopOnTaskRemoved,
            settings.audioFocusEnabled,
        )
        val snapshot = settingsStore.getSavedPlaybackSession()?.toSnapshot() ?: return null
        return snapshot.withoutHiddenTracks(hiddenIds(snapshot.queue.map { it.id }))
    }

    override suspend fun save(snapshot: EchoPlaybackSessionSnapshot?) {
        settingsStore.savePlaybackSession(snapshot?.toSavedSession())
    }
}

private fun EchoSavedPlaybackSession.toSnapshot(): EchoPlaybackSessionSnapshot =
    EchoPlaybackSessionSnapshot(
        queue = queue,
        currentIndex = currentIndex,
        positionMs = positionMs,
        playWhenReady = playWhenReady,
        shuffleEnabled = shuffleEnabled,
        repeatMode = repeatMode,
        playbackSpeed = playbackSpeed,
        playbackPitch = playbackPitch,
        shuffleOrder = shuffleOrder,
    )

private fun EchoPlaybackSessionSnapshot.toSavedSession(): EchoSavedPlaybackSession =
    EchoSavedPlaybackSession(
        queue = queue,
        currentIndex = currentIndex,
        positionMs = positionMs,
        playWhenReady = playWhenReady,
        shuffleEnabled = shuffleEnabled,
        repeatMode = repeatMode,
        playbackSpeed = playbackSpeed,
        playbackPitch = playbackPitch,
        shuffleOrder = shuffleOrder,
    )
