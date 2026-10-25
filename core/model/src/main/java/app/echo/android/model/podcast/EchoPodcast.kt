package app.echo.android.model.podcast

import app.echo.android.model.library.EchoTrack
import app.echo.android.model.library.LibrarySource

object PodcastLimits {
    const val Feeds = 100
    const val Episodes = 500
    const val PageSize = 40
    const val FeedBytes = 16 * 1024 * 1024
}

data class EchoPodcastFeed(
    val id: String, val url: String, val title: String, val author: String = "",
    val description: String = "", val artworkUrl: String? = null, val website: String? = null,
)

data class EchoPodcastEpisode(
    val id: String, val feedId: String, val title: String, val audioUrl: String,
    val description: String = "", val publishedMs: Long = 0, val durationMs: Long = 0,
    val artworkUrl: String? = null, val website: String? = null, val sourceOrder: Int = 0,
    val positionMs: Long = 0, val played: Boolean = false,
) {
    fun toTrack(feed: EchoPodcastFeed) = EchoTrack(
        id = id, uri = audioUrl, title = title, artist = feed.author.ifBlank { feed.title },
        album = feed.title, artworkUri = artworkUrl ?: feed.artworkUrl, durationMs = durationMs,
        source = LibrarySource("podcast"),
    )
}

enum class PodcastListenFilter { All, Unfinished, InProgress, Played }

data class PodcastEpisodePage(
    val episodes: List<EchoPodcastEpisode> = emptyList(), val page: Int = 0, val total: Int = 0,
) {
    val hasNext: Boolean get() = (page + 1) * PodcastLimits.PageSize < total
}
