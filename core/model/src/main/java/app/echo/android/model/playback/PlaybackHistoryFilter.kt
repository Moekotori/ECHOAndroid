package app.echo.android.model.playback

import java.time.DayOfWeek
import java.time.LocalDate

/** 历史页的日期范围。`Day` 取 [PlaybackHistoryFilter.fromEpochDay] 一天，`Custom` 取闭区间。 */
enum class PlaybackHistoryPeriod { All, Today, Week, Month, Year, Day, Custom }

/** 列表的组织方式：逐条时间线，或按曲目 / 专辑 / 艺术家聚合。 */
enum class PlaybackHistoryGrouping { Timeline, Tracks, Albums, Artists }

enum class PlaybackHistorySort {
    Recent, Plays, Time, Title, Artist;

    /** 时间线里每行只有一次播放，“播放次数”没有意义。 */
    fun isAvailableFor(grouping: PlaybackHistoryGrouping): Boolean =
        this != Plays || grouping != PlaybackHistoryGrouping.Timeline
}

/** 本地日历日的半开区间 [fromInclusive, untilExclusive)。空边界表示不限。 */
data class PlaybackHistoryDayBounds(val fromInclusive: Long?, val untilExclusive: Long?)

data class PlaybackHistoryFilter(
    val query: String = "",
    val period: PlaybackHistoryPeriod = PlaybackHistoryPeriod.All,
    /** `Day` / `Custom` 使用的 epoch day，闭区间。 */
    val fromEpochDay: Long? = null,
    val toEpochDay: Long? = null,
    val grouping: PlaybackHistoryGrouping = PlaybackHistoryGrouping.Timeline,
    val sort: PlaybackHistorySort = PlaybackHistorySort.Recent,
    val ascending: Boolean = false,
) {
    val isFiltered: Boolean get() = query.isNotBlank() || period != PlaybackHistoryPeriod.All

    /** 排序对当前分组不可用时回到“最近播放”。 */
    val effectiveSort: PlaybackHistorySort
        get() = if (sort.isAvailableFor(grouping)) sort else PlaybackHistorySort.Recent

    /** 周从周一开始，和 ECHO PC 一致。区间无效（缺日期或起点晚于终点）时不限范围。 */
    fun dayBounds(todayEpochDay: Long): PlaybackHistoryDayBounds {
        val today = LocalDate.ofEpochDay(todayEpochDay)
        return when (period) {
            PlaybackHistoryPeriod.All -> PlaybackHistoryDayBounds(null, null)
            PlaybackHistoryPeriod.Today -> PlaybackHistoryDayBounds(todayEpochDay, todayEpochDay + 1)
            PlaybackHistoryPeriod.Week -> {
                val monday = today.with(DayOfWeek.MONDAY).toEpochDay()
                PlaybackHistoryDayBounds(monday, monday + 7)
            }
            PlaybackHistoryPeriod.Month -> PlaybackHistoryDayBounds(
                today.withDayOfMonth(1).toEpochDay(),
                today.withDayOfMonth(1).plusMonths(1).toEpochDay(),
            )
            PlaybackHistoryPeriod.Year -> PlaybackHistoryDayBounds(
                today.withDayOfYear(1).toEpochDay(),
                today.withDayOfYear(1).plusYears(1).toEpochDay(),
            )
            PlaybackHistoryPeriod.Day -> fromEpochDay?.let { PlaybackHistoryDayBounds(it, it + 1) }
                ?: PlaybackHistoryDayBounds(null, null)
            PlaybackHistoryPeriod.Custom -> {
                val from = fromEpochDay
                val to = toEpochDay
                if (from == null || to == null || from > to) PlaybackHistoryDayBounds(null, null)
                else PlaybackHistoryDayBounds(from, to + 1)
            }
        }
    }
}

/** 当前筛选范围内的总览。 */
data class PlaybackHistorySummary(
    val plays: Int = 0,
    val listenedMs: Long = 0L,
    val tracks: Int = 0,
    val latestPlayedAtEpochMs: Long? = null,
)
