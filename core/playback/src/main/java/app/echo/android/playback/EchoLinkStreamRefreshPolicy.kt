package app.echo.android.playback

import app.echo.android.model.playback.EchoLinkPlaybackUri

object EchoLinkStreamRefreshPolicy {
    const val MaxAttempts = 5
    const val RetryCooldownMs = 1_500L
    const val ResolveTimeoutMs = 8_000L

    fun shouldAttempt(
        previousAttempts: Int,
        lastAttemptElapsedMs: Long,
        nowElapsedMs: Long,
    ): Boolean {
        if (previousAttempts <= 0) return true
        if (previousAttempts >= MaxAttempts) return false
        return nowElapsedMs - lastAttemptElapsedMs >= RetryCooldownMs
    }

    fun shouldPrefetchNext(
        nextMediaId: String?,
        nextUri: String,
        currentMediaId: String?,
    ): Boolean {
        if (nextMediaId.isNullOrBlank() || nextMediaId == currentMediaId) return false
        return EchoLinkPlaybackUri.requiresStreamResolve(nextMediaId, nextUri)
    }
}
