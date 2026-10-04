package app.echo.android.feature.player

import androidx.compose.ui.FrameRateCategory
import androidx.compose.ui.Modifier
import androidx.compose.ui.preferredFrameRate

/** Local hints expire with drawing; static/hidden lyrics do not hold a high-refresh window. */
internal fun Modifier.lyricsFrameRate(
    visible: Boolean,
    continuousMotion: Boolean,
    scrolling: Boolean = false,
    lightweight: Boolean,
    highPerformance: Boolean = false,
): Modifier = when (lyricsFrameRateHint(visible, continuousMotion, scrolling, lightweight, highPerformance)) {
    // Detaching the modifier clears its hint and avoids a layer on static content.
    LyricsFrameRateHint.None -> this
    LyricsFrameRateHint.Normal -> preferredFrameRate(FrameRateCategory.Normal)
    LyricsFrameRateHint.High -> preferredFrameRate(FrameRateCategory.High)
}

internal enum class LyricsFrameRateHint { None, Normal, High }

internal fun lyricsFrameRateHint(
    visible: Boolean,
    continuousMotion: Boolean,
    scrolling: Boolean,
    lightweight: Boolean,
    highPerformance: Boolean,
): LyricsFrameRateHint = when {
    !visible || (!continuousMotion && !scrolling) -> LyricsFrameRateHint.None
    lightweight -> LyricsFrameRateHint.Normal
    scrolling || highPerformance -> LyricsFrameRateHint.High
    else -> LyricsFrameRateHint.Normal
}
