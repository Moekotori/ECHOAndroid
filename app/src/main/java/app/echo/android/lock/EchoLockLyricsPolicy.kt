package app.echo.android.lock

internal object EchoLockLyricsPolicy {
    fun shouldKeepShowWhenLocked(
        enabled: Boolean,
        isPlaying: Boolean,
        hasCurrentLine: Boolean,
        dismissed: Boolean,
    ): Boolean = enabled && isPlaying && hasCurrentLine && !dismissed
}
