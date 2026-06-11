package app.echo.android.model.library

enum class LibraryTrackSortMode(
    val label: String,
) {
    Title("歌曲标题"),
    Duration("音乐时间"),
    FrequentlyPlayed("常听歌曲"),
    Random("随机排序"),
    Artist("艺术家"),
    Album("专辑"),
    RecentlyUpdated("最近更新"),
}
