package app.echo.android.feature.home

import app.echo.android.model.playback.PlaybackHistoryEntry
import app.echo.android.model.playback.PlaybackHistoryGrouping
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Column and heading text for the exported file; the caller resolves them in the user's language. */
data class PlaybackHistoryMarkdownLabels(
    val heading: String,
    val exported: String,
    val time: String,
    val title: String,
    val artist: String,
    val album: String,
    val plays: String,
    val listened: String,
    val lastPlayed: String,
)

/** Pure formatter so the export stays testable without a device. */
object PlaybackHistoryMarkdown {
    fun render(
        entries: List<PlaybackHistoryEntry>,
        grouping: PlaybackHistoryGrouping,
        labels: PlaybackHistoryMarkdownLabels,
        zone: ZoneId,
        locale: Locale,
    ): String {
        val stamp = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
        fun time(entry: PlaybackHistoryEntry) = stamp.format(Instant.ofEpochMilli(entry.playedAtEpochMs).atZone(zone))
        val columns: List<Pair<String, (PlaybackHistoryEntry) -> String>> = when (grouping) {
            PlaybackHistoryGrouping.Timeline -> listOf(
                labels.time to ::time,
                labels.title to { it.title },
                labels.artist to { it.artist },
                labels.album to { it.album.orEmpty() },
                labels.listened to { clock(it.listenedMs) },
            )
            PlaybackHistoryGrouping.Tracks -> listOf(
                labels.title to { it.title },
                labels.artist to { it.artist },
                labels.album to { it.album.orEmpty() },
                labels.plays to { it.plays.toString() },
                labels.listened to { clock(it.listenedMs) },
                labels.lastPlayed to ::time,
            )
            PlaybackHistoryGrouping.Albums -> listOf(
                labels.album to { it.title },
                labels.artist to { it.artist },
                labels.plays to { it.plays.toString() },
                labels.listened to { clock(it.listenedMs) },
                labels.lastPlayed to ::time,
            )
            PlaybackHistoryGrouping.Artists -> listOf(
                labels.artist to { it.title },
                labels.plays to { it.plays.toString() },
                labels.listened to { clock(it.listenedMs) },
                labels.lastPlayed to ::time,
            )
        }
        return buildString {
            append("# ").append(cell(labels.heading)).append("\n\n")
            append(cell(labels.exported)).append("\n\n")
            append("| ").append(columns.joinToString(" | ") { cell(it.first) }).append(" |\n")
            append("|").append(" --- |".repeat(columns.size)).append('\n')
            entries.forEach { entry ->
                append("| ").append(columns.joinToString(" | ") { cell(it.second(entry)) }).append(" |\n")
            }
        }
    }

    /** Table cells cannot contain a raw pipe or a line break. */
    internal fun cell(text: String): String =
        text.replace("\\", "\\\\").replace("|", "\\|").replace(Regex("\\s*[\\r\\n]+\\s*"), " ").trim()

    internal fun clock(ms: Long): String {
        val seconds = ms.coerceAtLeast(0L) / 1_000L
        return if (seconds >= 3_600L) "%d:%02d:%02d".format(Locale.ROOT, seconds / 3_600L, seconds / 60L % 60L, seconds % 60L)
        else "%d:%02d".format(Locale.ROOT, seconds / 60L, seconds % 60L)
    }
}
