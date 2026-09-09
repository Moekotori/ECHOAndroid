package app.echo.android.playback

object EchoLinkStreamRefreshPolicy {
    const val MaxAttempts = 5
    const val RetryCooldownMs = 1_500L

    fun shouldAttempt(
        previousAttempts: Int,
        lastAttemptElapsedMs: Long,
        nowElapsedMs: Long,
    ): Boolean {
        if (previousAttempts <= 0) return true
        if (previousAttempts >= MaxAttempts) return false
        return nowElapsedMs - lastAttemptElapsedMs >= RetryCooldownMs
    }
}
