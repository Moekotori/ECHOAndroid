package app.echo.android.playback

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
        isPlayingChanged: Boolean = false,
        tracksChanged: Boolean = false,
        playWhenReadyChanged: Boolean = false,
    ): Boolean {
        isPlayingChanged
        tracksChanged
        playWhenReadyChanged
        return timelineChanged
    }

    fun shouldSkipUnchangedSessionPersist(
        force: Boolean,
        signature: String,
        lastSignature: String?,
        positionBucket: Long,
        lastPositionBucket: Long?,
    ): Boolean = !force && signature == lastSignature && positionBucket == lastPositionBucket

    fun shouldReuseCachedQueueSnapshot(cachedItemCount: Int, playerItemCount: Int): Boolean =
        cachedItemCount > 0 && cachedItemCount == playerItemCount
}
