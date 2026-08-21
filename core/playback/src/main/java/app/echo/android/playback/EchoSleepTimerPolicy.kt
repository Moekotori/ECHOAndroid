package app.echo.android.playback

import app.echo.android.model.playback.EchoSleepTimerMode

object EchoSleepTimerPolicy {
    const val FadeMs = 8_000L
    const val UnknownRemainingMs = Long.MAX_VALUE

    fun remainingMs(
        mode: EchoSleepTimerMode,
        nowEpochMs: Long,
        timedEndEpochMs: Long?,
        trackRemainingMs: Long,
        trackDurationKnown: Boolean,
    ): Long =
        when (mode) {
            EchoSleepTimerMode.Off -> 0L
            EchoSleepTimerMode.Timed -> {
                val end = timedEndEpochMs ?: return 0L
                (end - nowEpochMs).coerceAtLeast(0L)
            }
            EchoSleepTimerMode.EndOfTrack -> if (trackDurationKnown) {
                trackRemainingMs.coerceAtLeast(0L)
            } else {
                UnknownRemainingMs
            }
        }

    fun fadeMultiplier(remainingMs: Long, fadeMs: Long = FadeMs): Float {
        if (remainingMs <= 0L) return 0f
        if (remainingMs == UnknownRemainingMs || remainingMs >= fadeMs) return 1f
        return (remainingMs.toFloat() / fadeMs.toFloat()).coerceIn(0f, 1f)
    }

    fun shouldPause(mode: EchoSleepTimerMode, remainingMs: Long): Boolean =
        mode != EchoSleepTimerMode.Off && remainingMs <= 0L

    fun shouldPauseAtEndOfMediaItem(mode: EchoSleepTimerMode): Boolean =
        mode == EchoSleepTimerMode.EndOfTrack

    fun shouldCancelEndOfTrackOnSeek(mode: EchoSleepTimerMode): Boolean =
        mode == EchoSleepTimerMode.EndOfTrack
}
