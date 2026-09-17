package app.echo.android.lock

object EchoLockLyricsPolicy {
    fun shouldShowOverLock(
        enabled: Boolean,
        isPlaying: Boolean,
        hasCurrentLine: Boolean,
        screenInteractive: Boolean,
        keyguardLocked: Boolean,
    ): Boolean = enabled && isPlaying && hasCurrentLine && screenInteractive && keyguardLocked

    fun shouldKeepShowWhenLocked(
        enabled: Boolean,
        isPlaying: Boolean,
        hasCurrentLine: Boolean,
    ): Boolean = enabled && isPlaying && hasCurrentLine
}
