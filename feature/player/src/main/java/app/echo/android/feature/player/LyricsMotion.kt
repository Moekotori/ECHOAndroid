package app.echo.android.feature.player

// One motion style for playback and previews; performance and system animation limits still apply.
internal const val DefaultLyricsMotionMode = "smooth"

/** Shared by the real lyric rows and the settings demo; transforms never change line wrapping. */
internal data class LyricsMotionProfile(
    val intensity: Float,
    val focusStiffness: Float,
    val followStiffness: Float,
    val nearbyScale: Float,
    val distantScale: Float,
    val liftDp: Float,
    /** Blur radius added per line of distance from the focused line (capped at 3 lines). */
    val blurDp: Float = 0f,
) {
    fun blur(focused: Boolean, distance: Int): Float = if (focused) 0f else blurDp * distance.coerceIn(0, 3)

    fun scale(focused: Boolean, distance: Int): Float = when {
        focused -> 1f
        distance <= 1 -> nearbyScale
        else -> distantScale
    }
}

private val CalmLyricsMotion = LyricsMotionProfile(0f, 360f, 320f, 1f, 1f, 0f)
private val SmoothLyricsMotion = LyricsMotionProfile(0.55f, 190f, 170f, 0.94f, 0.90f, 4f, 0.5f)
private val StageLyricsMotion = LyricsMotionProfile(1f, 115f, 105f, 0.86f, 0.78f, 14f, 1.2f)

internal fun lyricsMotionProfile(mode: String): LyricsMotionProfile = when (mode) {
    "calm" -> CalmLyricsMotion
    "stage" -> StageLyricsMotion
    else -> SmoothLyricsMotion
}
