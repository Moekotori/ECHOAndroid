package app.echo.android

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
}
