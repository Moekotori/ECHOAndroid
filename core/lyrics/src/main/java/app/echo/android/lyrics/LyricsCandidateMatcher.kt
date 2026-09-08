package app.echo.android.lyrics

import kotlin.math.abs

/** Prepare the target once per search; provider scores share the same scale. */
internal class LyricsCandidateMatcher(private val request: EchoLyricsSearchRequest) {
    private val title = request.title.lyricsMatchKey()
    private val artists = lyricsArtistGroups(request.artist)
    private val album = request.album.orEmpty().lyricsMatchKey()
    private val versions = lyricsVersionKeys(request.title)
    private val titles = lyricsTitleKeys(request.title)
    private val languages = lyricsLanguageVersions(request.title, request.album)

    fun match(
        candidateTitle: String, candidateArtist: String, candidateAlbum: String?, durationMs: Long,
        titleAliases: List<String> = emptyList(), artistAliases: List<List<String>> = emptyList(),
    ): Match? {
        val otherLanguages = lyricsLanguageVersions(candidateTitle, candidateAlbum)
        // Explicit singing-language conflicts override matching names, albums and duration.
        if (languages.isNotEmpty() && otherLanguages.isNotEmpty() && languages != otherLanguages) return null
        val otherTitle = candidateTitle.lyricsMatchKey()
        val otherArtists = lyricsArtistGroups(candidateArtist, artistAliases)
        if (title.isBlank() || otherTitle.isBlank() || artists.isEmpty() || otherArtists.isEmpty()) return null
        val exactTitle = titles.intersect(lyricsTitleKeys(candidateTitle, titleAliases)).isNotEmpty()
        val characterCredit = hasLyricsCharacterCredit(request.artist) || hasLyricsCharacterCredit(candidateArtist)
        val exactArtists = lyricsArtistsMatch(artists, otherArtists) &&
            (!characterCredit || request.artist.lyricsMatchKey() == candidateArtist.lyricsMatchKey())
        // Never use substrings of artist names as identity evidence (Ann != Joanne).
        if (!exactArtists && artists.none { target -> otherArtists.any { target.intersect(it).isNotEmpty() } }) return null
        if (!exactTitle && !title.contains(otherTitle) && !otherTitle.contains(title)) return null
        if (versions != lyricsVersionKeys(candidateTitle)) return null
        val delta = if (request.durationMs > 0 && durationMs > 0) abs(request.durationMs - durationMs) else null
        if (delta != null && delta > 15_000L) return null
        val otherAlbum = candidateAlbum.orEmpty().lyricsMatchKey()
        val exactAlbum = album.isNotEmpty() && album == otherAlbum
        val conflictingAlbum = album.isNotEmpty() && otherAlbum.isNotEmpty() && !exactAlbum
        val score = (if (exactTitle) 60 else 28) + (if (exactArtists) 28 else 18) +
            (if (exactAlbum) 12 else 0) + when {
                delta == null -> 0
                delta <= 3_000L -> 24
                delta <= 8_000L -> 12
                else -> -14
            }
        // Partial names and missing supporting metadata belong in the manual picker only.
        val automatic = exactTitle && exactArtists && when {
            delta == null -> exactAlbum
            conflictingAlbum -> delta <= 3_000L
            else -> delta <= 8_000L
        }
        return Match(score, automatic, automatic && delta != null && delta <= 3_000L && !conflictingAlbum)
    }

    data class Match(val score: Int, val automatic: Boolean, val fast: Boolean)
}
