package app.echo.android.playback

import app.echo.android.model.playback.EchoRepeatMode

enum class PlaybackQueueReplaceIntent {
    PlayAll,
    Shuffle,
}

object PlaybackSessionPolicy {
    fun hasBlockingPendingPlay(
        pendingQueueReplace: Boolean,
        pendingAuthRestorePlay: Boolean,
    ): Boolean = pendingQueueReplace || pendingAuthRestorePlay

    fun shouldPersistSavedSession(
        restoreCompleted: Boolean,
        hasPendingPlay: Boolean,
        queueEmpty: Boolean,
    ): Boolean {
        if (hasPendingPlay) return false
        if (!restoreCompleted && queueEmpty) return false
        return true
    }

    /**
     * USB 独占切轨时是否需要静音过渡。仅当会话无法无缝复用（未在流式输出、
     * 采样率未知或即将变化，需要重建 USB 会话）时才静音，否则保持 gapless。
     */
    fun shouldMuteUsbTransition(
        exclusiveStreaming: Boolean,
        streamingSampleRateHz: Int?,
        nextTrackSampleRateHz: Int?,
    ): Boolean {
        if (!exclusiveStreaming) return true
        val current = streamingSampleRateHz?.takeIf { it > 0 } ?: return true
        val next = nextTrackSampleRateHz?.takeIf { it > 0 } ?: return true
        return next != current
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

    fun shouldForceAudioSinkReset(
        exclusiveEnabledChanged: Boolean,
        usbRouteLost: Boolean,
        hostPermissionNewlyGranted: Boolean,
    ): Boolean = exclusiveEnabledChanged || usbRouteLost || hostPermissionNewlyGranted

    fun shouldRemapFullQueue(
        timelineChanged: Boolean,
        mediaItemTransitioned: Boolean = false,
        isPlayingChanged: Boolean = false,
        tracksChanged: Boolean = false,
        playWhenReadyChanged: Boolean = false,
    ): Boolean {
        // isPlayingChanged / tracksChanged / playWhenReadyChanged are part of the call-site contract
        // but must not rebuild the queue on their own; only timeline identity forces a full remap.
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

    fun queueStartIndex(queueIds: List<String>, tappedId: String): Int =
        queueIds.indexOfFirst { it == tappedId }.takeIf { it >= 0 } ?: 0

    fun shouldPrepareAfterExternalSkip(
        hasPlayerError: Boolean,
        playbackStateIdle: Boolean,
        mediaItemCount: Int,
    ): Boolean = mediaItemCount > 0 && shouldPrepareBeforePlay(hasPlayerError, playbackStateIdle)
}
