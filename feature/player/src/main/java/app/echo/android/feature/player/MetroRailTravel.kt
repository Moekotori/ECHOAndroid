package app.echo.android.feature.player

import androidx.compose.runtime.Immutable
import app.echo.android.model.lyrics.EchoLyricLine

/**
 * The train's trip along the rail from the current lyric station to the next one. Decorative only:
 * it is derived from the playback position and never feeds anything back.
 */
@Immutable
internal class MetroRailTravel(val fromMs: Long, val toMs: Long) {
    /** 0 while the train waits at the current station, 1 once it reaches the next. */
    fun progressAt(positionMs: Long): Float {
        val span = toMs - fromMs
        if (span <= 0L) return 0f
        return ((positionMs - fromMs).toFloat() / span).coerceIn(0f, 1f)
    }
}

/**
 * The trip spans two list rows: the current row owns the first half of it and the next row the second half,
 * because neither row knows how tall the other is. These map the whole trip to each row's own 0..1 stretch.
 */
internal fun metroDepartFill(progress: Float): Float = (progress * 2f).coerceIn(0f, 1f)

internal fun metroArriveFill(progress: Float): Float = ((progress - 0.5f) * 2f).coerceIn(0f, 1f)

/** Only the current row and the one after it take part; every other row gets null. */
internal fun metroRailTravel(lines: List<EchoLyricLine>, index: Int, focusIndex: Int): MetroRailTravel? {
    if (focusIndex < 0 || (index != focusIndex && index != focusIndex + 1)) return null
    val from = lines.getOrNull(focusIndex)?.startMs ?: return null
    val to = lines.getOrNull(focusIndex + 1)?.startMs ?: return null
    return if (from >= 0L && to > from) MetroRailTravel(from, to) else null
}
