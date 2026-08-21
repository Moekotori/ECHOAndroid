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

    fun fadeMultiplier(
        remainingMs: Long,
        fadeMs: Long = FadeMs,
        allowFade: Boolean = true,
        mode: EchoSleepTimerMode = EchoSleepTimerMode.Timed,
    ): Float {
        if (mode == EchoSleepTimerMode.Off || !allowFade) return 1f
        if (remainingMs <= 0L) return 0f
        if (remainingMs == UnknownRemainingMs || remainingMs >= fadeMs) return 1f
        return (remainingMs.toFloat() / fadeMs.toFloat()).coerceIn(0f, 1f)
    }

    fun isTimedMinutesSelected(requestedMinutes: Int?, optionMinutes: Int): Boolean =
        requestedMinutes != null && requestedMinutes == optionMinutes

    fun shouldPause(mode: EchoSleepTimerMode, remainingMs: Long): Boolean =
        mode != EchoSleepTimerMode.Off && remainingMs <= 0L

    fun shouldPauseAtEndOfMediaItem(mode: EchoSleepTimerMode): Boolean =
        mode == EchoSleepTimerMode.EndOfTrack

    fun shouldCancelEndOfTrackOnSeek(mode: EchoSleepTimerMode): Boolean =
        mode == EchoSleepTimerMode.EndOfTrack

    fun tickMs(remainingMs: Long, fadeMs: Long = FadeMs): Long =
        if (remainingMs in 1 until fadeMs) 250L else 1_000L
}
