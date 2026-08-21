package app.echo.android

import app.echo.android.model.playback.EchoPlaybackState

internal object LastFmScrobbleRules {
    const val MIN_TRACK_DURATION_MS = 30_000L
    const val MAX_LISTEN_BEFORE_SCROBBLE_MS = 240_000L

    fun shouldScrobble(durationMs: Long, listenedMs: Long): Boolean {
        if (listenedMs <= 0L) return false
        val threshold = when {
            durationMs <= 0L -> MAX_LISTEN_BEFORE_SCROBBLE_MS
            durationMs <= MIN_TRACK_DURATION_MS -> return false
            else -> minOf(durationMs / 2L, MAX_LISTEN_BEFORE_SCROBBLE_MS)
        }
        return listenedMs >= threshold
    }

    fun accumulatedPlayMs(
        previouslyAccumulatedMs: Long,
        wasPlaying: Boolean,
        lastTickEpochMs: Long,
        nowEpochMs: Long,
    ): Long {
        if (!wasPlaying || lastTickEpochMs <= 0L || nowEpochMs <= lastTickEpochMs) {
            return previouslyAccumulatedMs
        }
        return previouslyAccumulatedMs + (nowEpochMs - lastTickEpochMs)
    }

    fun keepSubmittedFlag(attemptSucceeded: Boolean): Boolean = attemptSucceeded

    fun shouldAttemptSubmit(
        alreadySubmitted: Boolean,
        lastAttemptEpochMs: Long,
        nowEpochMs: Long,
    ): Boolean {
        if (alreadySubmitted) return false
        if (lastAttemptEpochMs <= 0L) return true
        return nowEpochMs - lastAttemptEpochMs >= SUBMIT_RETRY_BACKOFF_MS
    }

    fun shouldClearActiveScrobble(credentialsReady: Boolean): Boolean = !credentialsReady

    fun shouldClearActiveScrobbleForMissingTrack(playbackState: EchoPlaybackState): Boolean =
        playbackState == EchoPlaybackState.Idle ||
            playbackState == EchoPlaybackState.Stopped ||
            playbackState == EchoPlaybackState.Ended ||
            playbackState == EchoPlaybackState.Error

    fun shouldFlushScrobbleBeforeReplacing(
        alreadyScrobbled: Boolean,
        durationMs: Long,
        listenedMs: Long,
    ): Boolean = !alreadyScrobbled && shouldScrobble(durationMs, listenedMs)

    fun shouldStartNewListenAfterRepeat(
        alreadyScrobbled: Boolean,
        previousPositionMs: Long,
        currentPositionMs: Long,
    ): Boolean = alreadyScrobbled &&
        previousPositionMs >= REPEAT_PREVIOUS_POSITION_MS &&
        currentPositionMs <= REPEAT_RESTART_POSITION_MS &&
        previousPositionMs - currentPositionMs >= REPEAT_POSITION_DROP_MS
}

private const val REPEAT_PREVIOUS_POSITION_MS = 8_000L
private const val REPEAT_RESTART_POSITION_MS = 3_000L
private const val REPEAT_POSITION_DROP_MS = 5_000L
private const val SUBMIT_RETRY_BACKOFF_MS = 30_000L
