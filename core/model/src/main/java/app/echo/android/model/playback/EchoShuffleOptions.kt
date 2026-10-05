package app.echo.android.model.playback

data class EchoShuffleOptions(
    val mode: EchoShuffleMode = EchoShuffleMode.PseudoRandom,
    val spreadArtists: Boolean = true,
    val recentTrackCount: Int = 5,
) {
    val normalized: EchoShuffleOptions
        get() {
            val count = recentTrackCount.coerceIn(0, MaxRecentTracks)
            return copy(recentTrackCount = RecentTrackCounts.minBy { kotlin.math.abs(it - count) })
        }

    companion object {
        const val MaxRecentTracks = 20
        val RecentTrackCounts = listOf(0, 3, 5, 10, 20)
    }
}
