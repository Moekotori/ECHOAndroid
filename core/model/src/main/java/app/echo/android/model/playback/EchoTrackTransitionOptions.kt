package app.echo.android.model.playback

/** Gapless queue playback is the default; fades are an optional gain envelope. */
data class EchoTrackTransitionOptions(
    val fadeEnabled: Boolean = false,
    val fadeDurationMs: Int = 1500,
) {
    fun normalized() = copy(fadeDurationMs = fadeDurationMs.coerceIn(500, 5000))
}
