package app.echo.android.model.playback

/** Gapless queue playback is the default; fades are an optional gain envelope. */
data class EchoTrackTransitionOptions(
    val fadeEnabled: Boolean = false,
    val fadeDurationMs: Int = 1500,
    val smartEnabled: Boolean = false,
) {
    fun normalized() = copy(fadeDurationMs = roundFadeDurationMs(fadeDurationMs))
}

fun roundFadeDurationMs(durationMs: Int): Int {
    val clamped = durationMs.coerceIn(MinTrackFadeDurationMs, MaxTrackFadeDurationMs)
    return ((clamped + 50) / 100) * 100
}

const val MinTrackFadeDurationMs = 500
const val MaxTrackFadeDurationMs = 5_000
