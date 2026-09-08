package app.echo.android.model.library

/** Online reference data is separate from the user's file tags. */
data class AlbumOnlineInfo(
    val releaseId: String,
    val releaseTitle: String,
    val artist: String,
    val date: String?,
    val country: String?,
    val labels: List<String>,
    val catalogNumbers: List<String>,
    val credits: List<AlbumOnlineCredit>,
    val description: String?,
    val wikipediaUrl: String?,
    val wikipediaLanguage: String?,
    val fetchedAtMs: Long,
    val partial: Boolean = false,
    val cached: Boolean = false,
    val stale: Boolean = false,
)

data class AlbumOnlineCredit(val role: String, val name: String, val track: String? = null)

fun interface AlbumOnlineInfoLoader {
    /** Null means no confidently matching release; failures are exceptions, not empty results. */
    suspend fun load(album: AlbumSummary, language: String, refresh: Boolean): AlbumOnlineInfo?
}
