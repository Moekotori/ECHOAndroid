package app.echo.android.model.playback

/** 统计页的时间范围。`All` 不限起点。 */
enum class ListeningStatsRange(val days: Long?) {
    Week(7L),
    Month(30L),
    Year(365L),
    All(null),
}

/** 一条排行记录：曲目、专辑或艺术家。`key` 在同一张榜内唯一，曲目榜里就是 trackId。 */
data class ListeningStatsEntry(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val artworkUri: String? = null,
    val playCount: Int,
    val listenedMs: Long,
)

data class ListeningStats(
    val range: ListeningStatsRange = ListeningStatsRange.Month,
    val totalPlays: Int = 0,
    val totalListenedMs: Long = 0L,
    val activeDays: Int = 0,
    val topTracks: List<ListeningStatsEntry> = emptyList(),
    val topAlbums: List<ListeningStatsEntry> = emptyList(),
    val topArtists: List<ListeningStatsEntry> = emptyList(),
    /** 24 个元素，下标是本地时间的小时。 */
    val playsByHour: List<Int> = List(24) { 0 },
) {
    val isEmpty: Boolean get() = totalPlays == 0
}
