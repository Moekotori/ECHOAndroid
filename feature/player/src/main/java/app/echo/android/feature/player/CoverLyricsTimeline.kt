package app.echo.android.feature.player

import app.echo.android.model.lyrics.EchoLyricLine

/** Keeps timing boundaries (including blank cues) out of the three visible rows. */
internal class CoverLyricsTimeline(private val lines: List<EchoLyricLine>) {
    private val visible = lines.indices.filter { lines[it].startMs >= 0L && lines[it].text.isNotBlank() }
    private val timeline = LyricsTimeline(lines)

    val isEmpty: Boolean get() = visible.isEmpty()

    fun activeAt(positionMs: Long): Int = timeline.activeAt(positionMs).minOrNull() ?: -1

    fun contextAt(positionMs: Long): Int =
        timeline.contextAt(positionMs).takeIf { it >= 0 } ?: visible.firstOrNull() ?: -1

    fun slotOf(index: Int): Int = visible.binarySearch(index)

    fun textAt(slot: Int): String? = visible.getOrNull(slot)?.let { lines[it].text.trim() }
}
