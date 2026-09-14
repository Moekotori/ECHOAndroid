package app.echo.android.model.library

/** Reference information never overwrites local tags or Echo Link artist identities. */
data class ArtistOnlineQuery(val name: String, val albumTitles: List<String> = emptyList())

data class ArtistOnlineInfo(
    val musicBrainzId: String,
    val name: String,
    val kind: String?,
    val area: String?,
    val begin: String?,
    val end: String?,
    val aliases: List<String>,
    val genres: List<String>,
    val members: List<String>,
    val description: String? = null,
    val wikipediaUrl: String? = null,
    val wikipediaLanguage: String? = null,
    val partial: Boolean = false,
    val stale: Boolean = false,
)

data class ArtistConcert(
    val id: String,
    val title: String,
    /** Local calendar date at the venue. Never converted through the phone timezone. */
    val date: String,
    val time: String?,
    val venue: String?,
    val city: String?,
    val source: String,
    val url: String,
    val ticketUrl: String? = null,
)

data class ArtistConcerts(
    val events: List<ArtistConcert>,
    val failedSources: List<String> = emptyList(),
    val stale: Boolean = false,
)

interface ArtistOnlineInfoLoader {
    /** Null means no unambiguous match. Network and parse failures throw. */
    suspend fun loadProfile(query: ArtistOnlineQuery, language: String, refresh: Boolean): ArtistOnlineInfo?
    suspend fun loadConcerts(query: ArtistOnlineQuery, refresh: Boolean): ArtistConcerts
}
