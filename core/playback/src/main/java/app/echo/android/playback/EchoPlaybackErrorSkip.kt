package app.echo.android.playback

import app.echo.android.model.playback.EchoAudioErrorKind
import app.echo.android.model.playback.EchoPlaybackError

fun EchoPlaybackError.shouldAutoSkipTrack(): Boolean =
    !recoverable || kind == EchoAudioErrorKind.DecodeFailure

fun nextIndexAfterPlaybackError(
    currentIndex: Int,
    mediaItemCount: Int,
    repeatAll: Boolean,
    consecutiveErrorSkips: Int,
    shuffledNextIndex: Int? = null,
): Int? {
    if (mediaItemCount <= 1) return null
    if (consecutiveErrorSkips >= mediaItemCount) return null
    if (currentIndex !in 0 until mediaItemCount) return null
    val nextIndex = shuffledNextIndex ?: when {
        currentIndex + 1 < mediaItemCount -> currentIndex + 1
        repeatAll -> 0
        else -> null
    }
    return nextIndex?.takeIf { it in 0 until mediaItemCount && it != currentIndex }
}
