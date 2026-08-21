package app.echo.android

import app.echo.android.data.EchoSavedPlaybackSession
import app.echo.android.data.EchoSettingsStore
import app.echo.android.playback.EchoPlaybackSessionSnapshot
import app.echo.android.playback.EchoPlaybackSessionStore

class EchoSettingsPlaybackSessionStore(
    private val settingsStore: EchoSettingsStore,
) : EchoPlaybackSessionStore {
    override suspend fun load(): EchoPlaybackSessionSnapshot? =
        settingsStore.getSavedPlaybackSession()?.toSnapshot()

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
    )
