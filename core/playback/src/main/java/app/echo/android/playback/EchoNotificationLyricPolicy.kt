package app.echo.android.playback

import app.echo.android.model.lyrics.EchoLyricDisplaySnapshot
import app.echo.android.model.lyrics.EchoLyricLine
import app.echo.android.model.lyrics.EchoLyrics

data class EchoNotificationLyricLine(
    val startMs: Long,
    val text: String,
)

data class EchoNotificationLyricDocument(
    val trackId: String,
    val lines: List<EchoNotificationLyricLine>,
)

object EchoNotificationLyricPolicy {
    const val MaxChars = 90
    const val MinUpdateIntervalMs = 500L
    const val MinScheduleDelayMs = 50L

    fun document(trackId: String?, lines: List<Pair<Long, String>>): EchoNotificationLyricDocument? {
        val id = trackId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val timed = lines.mapNotNull { (startMs, text) ->
            val trimmed = collapseText(text)
            if (startMs < 0L || trimmed.isEmpty()) null else EchoNotificationLyricLine(startMs, trimmed)
        }
        if (timed.isEmpty()) return null
        return EchoNotificationLyricDocument(id, timed)
    }

    fun primaryText(lines: List<EchoNotificationLyricLine>, positionMs: Long): String? {
        val index = lastStartedIndex(lines, positionMs)
        if (index < 0) return null
        val line = lines[index]
        return line.text.takeIf { it.isNotEmpty() }
    }

    fun nextStartMs(lines: List<EchoNotificationLyricLine>, positionMs: Long): Long? {
        val index = lastStartedIndex(lines, positionMs)
        val next = index + 1
        if (next in lines.indices) return lines[next].startMs
        return null
    }

    fun delayMs(positionMs: Long, nextStartMs: Long, speed: Float): Long {
        val rate = speed.takeIf { it.isFinite() && it > 0f } ?: 1f
        val remaining = (nextStartMs - positionMs).coerceAtLeast(0L)
        return (remaining / rate).toLong().coerceAtLeast(MinScheduleDelayMs)
    }

    fun clampText(text: String): String {
        val collapsed = collapseText(text)
        if (collapsed.length <= MaxChars) return collapsed
        return collapsed.take(MaxChars - 1).trimEnd() + "…"
    }

    fun shouldPublish(
        previous: String?,
        next: String?,
        elapsedSincePublishMs: Long,
        minIntervalMs: Long = MinUpdateIntervalMs,
    ): Boolean {
        if (previous == next) return false
        if (previous == null || next == null) return true
        return elapsedSincePublishMs >= minIntervalMs
    }

    fun snapshot(
        trackId: String?,
        lyrics: EchoLyrics?,
        lines: List<EchoNotificationLyricLine>,
        positionMs: Long,
        isPlaying: Boolean,
        speed: Float,
        publishedAtElapsedRealtimeMs: Long,
    ): EchoLyricDisplaySnapshot {
        val synced = lyrics?.takeIf { it.isSynced }?.lines?.filter { it.startMs >= 0L && it.text.isNotBlank() }
        return if (!synced.isNullOrEmpty()) {
            snapshotFromLines(trackId, synced, positionMs, isPlaying, speed, publishedAtElapsedRealtimeMs)
        } else {
            snapshotFromLines(
                trackId,
                lines.map { EchoLyricLine(startMs = it.startMs, text = it.text) },
                positionMs,
                isPlaying,
                speed,
                publishedAtElapsedRealtimeMs,
            )
        }
    }

    fun snapshotFromLines(
        trackId: String?,
        lines: List<EchoLyricLine>,
        positionMs: Long,
        isPlaying: Boolean,
        speed: Float,
        publishedAtElapsedRealtimeMs: Long,
    ): EchoLyricDisplaySnapshot {
        val index = lastStartedLyricIndex(lines, positionMs)
        val current = lines.getOrNull(index)
        return EchoLyricDisplaySnapshot(
            trackId = trackId,
            previous = lines.getOrNull(index - 1),
            current = current,
            next = lines.getOrNull(index + 1),
            currentStartMs = current?.startMs ?: 0L,
            positionMs = positionMs.coerceAtLeast(0L),
            publishedAtElapsedRealtimeMs = publishedAtElapsedRealtimeMs,
            isPlaying = isPlaying,
            speed = speed.takeIf { it.isFinite() && it > 0f } ?: 1f,
        )
    }

    fun lastStartedIndex(lines: List<EchoNotificationLyricLine>, positionMs: Long): Int =
        lastStartedByStartMs(lines.size, positionMs) { lines[it].startMs }

    fun lastStartedLyricIndex(lines: List<EchoLyricLine>, positionMs: Long): Int =
        lastStartedByStartMs(lines.size, positionMs) { lines[it].startMs }

    private fun lastStartedByStartMs(size: Int, positionMs: Long, startMsAt: (Int) -> Long): Int {
        var low = 0
        var high = size - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (startMsAt(mid) <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    private fun collapseText(text: String): String =
        text.replace('\n', ' ').replace('\r', ' ').replace(Whitespace, " ").trim()
}

private val Whitespace = Regex("\\s+")
