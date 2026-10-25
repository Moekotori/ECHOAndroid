package app.echo.android.feature.podcasts

import app.echo.android.model.podcast.*

data class PodcastScreenState(
    val feeds: List<EchoPodcastFeed> = emptyList(), val visibleFeeds: List<EchoPodcastFeed> = emptyList(),
    val selected: EchoPodcastFeed? = null, val episodes: PodcastEpisodePage = PodcastEpisodePage(),
    val feedQuery: String = "", val episodeQuery: String = "", val filter: PodcastListenFilter = PodcastListenFilter.All,
    val artworkRevision: Int = 0,
    val loading: Boolean = false, val busy: Boolean = false, val failed: Boolean = false,
)

class PodcastActions(
    val select: (EchoPodcastFeed?) -> Unit,
    val searchFeeds: (String) -> Unit,
    val searchEpisodes: (String) -> Unit,
    val filter: (PodcastListenFilter) -> Unit,
    val page: (Int) -> Unit,
    val subscribe: suspend (String) -> Unit,
    val refresh: () -> Unit,
    val unfollow: () -> Unit,
    val retry: () -> Unit,
    val listen: () -> Unit,
    val play: (EchoPodcastEpisode, Boolean) -> Unit,
    val enqueue: (EchoPodcastEpisode, Boolean) -> Unit,
    val markPlayed: (EchoPodcastEpisode) -> Unit,
    val openUrl: (String) -> Unit,
)

