package app.echo.android.model.library

enum class PlaylistTrackSort { Default, Title, Artist, Album, Duration, RecentlyAdded }

data class PlaylistTrackOrder(
    val sort: PlaylistTrackSort = PlaylistTrackSort.Default,
    val descending: Boolean = false,
) {
    val isManual: Boolean get() = sort == PlaylistTrackSort.Default
}
