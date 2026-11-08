package app.echo.android.feature.home

import app.echo.android.model.playback.PlaybackHistoryEntry
import app.echo.android.model.playback.PlaybackHistoryGrouping
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackHistoryMarkdownTest {
    private val labels = PlaybackHistoryMarkdownLabels("History", "Exported", "Time", "Title", "Artist", "Album", "Plays", "Listened", "Last")
    private val entry = PlaybackHistoryEntry(1, "t", "A | B\nC", "Artist", null, null, 0L, 125_000L, true, plays = 3)

    private fun table(grouping: PlaybackHistoryGrouping) =
        PlaybackHistoryMarkdown.render(listOf(entry), grouping, labels, ZoneOffset.UTC, Locale.ENGLISH)
            .lines().filter { it.startsWith("|") }

    @Test fun pipesAndLineBreaksCannotBreakTheTable() {
        val row = table(PlaybackHistoryGrouping.Tracks)[2]
        assertTrue(row, row.startsWith("| A \\| B C | Artist |  | 3 | 2:05 |"))
    }

    @Test fun everyModeHasHeaderSeparatorAndOneRow() {
        for (grouping in PlaybackHistoryGrouping.entries) {
            val lines = table(grouping)
            assertEquals(grouping.name, 3, lines.size)
            assertTrue(grouping.name, lines[1].all { it == '|' || it == ' ' || it == '-' })
            assertEquals(grouping.name, lines[0].count { it == '|' }, lines[1].count { it == '|' })
        }
    }

    @Test fun clockUsesHoursOnlyWhenNeeded() {
        assertEquals("0:59", PlaybackHistoryMarkdown.clock(59_900L))
        assertEquals("1:01:01", PlaybackHistoryMarkdown.clock(3_661_000L))
        assertEquals("0:00", PlaybackHistoryMarkdown.clock(-5L))
    }
}
