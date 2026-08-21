package app.echo.android.playback

import app.echo.android.model.playback.EchoPlaybackStatus
import app.echo.android.model.playback.EchoRepeatMode
import app.echo.android.model.playback.PlaybackPositionState

enum class PlaybackQueueReplaceIntent {
    PlayAll,
    Shuffle,
}

object PlaybackSessionPolicy {
    fun shouldPersistSavedSession(
        restoreCompleted: Boolean,
        hasPendingPlay: Boolean,
        queueEmpty: Boolean,
    ): Boolean {
        if (hasPendingPlay) return false
        if (!restoreCompleted && queueEmpty) return false
        return true
    }

    fun restoredVolumeAfterUsbMute(capturedVolume: Float, fallbackVolume: Float): Float {
        if (!capturedVolume.isFinite() || capturedVolume <= 0f) {
            return fallbackVolume.coerceAtLeast(0f)
        }
        return capturedVolume
    }

    fun shouldPrepareBeforePlay(hasPlayerError: Boolean, playbackStateIdle: Boolean): Boolean =
        hasPlayerError || playbackStateIdle

    fun shouldClaimUsbInterfaceForDriverTest(isPlayingToUsb: Boolean): Boolean = !isPlayingToUsb

    fun shouldRemapFullQueue(
        timelineChanged: Boolean,
        mediaItemTransitioned: Boolean = false,
        isPlayingChanged: Boolean = false,
        tracksChanged: Boolean = false,
        playWhenReadyChanged: Boolean = false,
    ): Boolean {
        isPlayingChanged
        tracksChanged
        playWhenReadyChanged
        return timelineChanged || mediaItemTransitioned
    }

    fun shouldSkipUnchangedSessionPersist(
        force: Boolean,
        signature: String,
        lastSignature: String?,
        positionBucket: Long,
        lastPositionBucket: Long?,
        persistBecauseOfSeek: Boolean = false,
    ): Boolean = !force &&
        !persistBecauseOfSeek &&
        signature == lastSignature &&
        positionBucket == lastPositionBucket

    fun shouldReuseCachedQueueSnapshot(
        cachedMediaIds: List<String>,
        playerMediaIds: List<String>,
    ): Boolean = cachedMediaIds.isNotEmpty() && cachedMediaIds == playerMediaIds

    fun shouldPersistNullSavedSession(mediaItemCount: Int): Boolean = mediaItemCount <= 0

    fun shuffleEnabledForQueueReplace(intent: PlaybackQueueReplaceIntent): Boolean =
        intent == PlaybackQueueReplaceIntent.Shuffle

    fun repeatModeForQueueReplace(intent: PlaybackQueueReplaceIntent): EchoRepeatMode =
        EchoRepeatMode.Off

    fun skipShouldCallPlay(): Boolean = false

    fun playbackStatusWithLivePosition(
        status: EchoPlaybackStatus,
        position: PlaybackPositionState,
    ): EchoPlaybackStatus =
        status.copy(
            positionMs = position.positionMs,
            durationMs = if (position.durationMs > 0L) position.durationMs else status.durationMs,
        )
}
