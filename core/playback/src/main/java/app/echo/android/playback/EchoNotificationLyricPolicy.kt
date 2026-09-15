package app.echo.android.playback

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

    private fun lastStartedIndex(lines: List<EchoNotificationLyricLine>, positionMs: Long): Int {
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].startMs <= positionMs) {
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
