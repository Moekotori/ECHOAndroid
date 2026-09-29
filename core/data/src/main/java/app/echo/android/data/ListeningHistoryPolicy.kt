package app.echo.android.data

import app.echo.android.model.playback.ListeningStatsRange
import java.time.Instant
import java.time.ZoneId

object ListeningHistoryPolicy {
    /** 很短的曲目（铃声、片段）听完一半也算；其余听满 30 秒算一次。 */
    const val MIN_LISTEN_MS = 30_000L

    fun countsAsListen(durationMs: Long, listenedMs: Long): Boolean {
        if (listenedMs <= 0L) return false
        if (durationMs in 1 until MIN_LISTEN_MS * 2) return listenedMs * 2 >= durationMs
        return listenedMs >= MIN_LISTEN_MS
    }

    /** 范围从本地日期的零点开始，今天算作第一天。 */
    fun rangeStartEpochMs(range: ListeningStatsRange, nowEpochMs: Long, zone: ZoneId): Long {
        val days = range.days ?: return 0L
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        return today.minusDays(days - 1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
