package app.echo.android.lyrics

import kotlin.math.abs

/** Prepare the target once per search; provider scores share the same scale. */
internal class LyricsCandidateMatcher(private val request: EchoLyricsSearchRequest) {
    private val title = request.title.lyricsMatchKey()
    private val artists = lyricsArtistGroups(request.artist)
    private val album = request.album.orEmpty().lyricsMatchKey()
    private val arrangements = lyricsArrangementKeys(request.title)
    private val coverTagged = lyricsCoverTagged(request.title)
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
        val artistOverlap = artists.any { target -> otherArtists.any { target.intersect(it).isNotEmpty() } }
        if (arrangements != lyricsArrangementKeys(candidateTitle)) return null
        val delta = if (request.durationMs > 0 && durationMs > 0) abs(request.durationMs - durationMs) else null
        if (delta != null && delta > 15_000L) return null
        val crossArtist = !exactArtists && !artistOverlap
        if (crossArtist) {
            // Same lyrics, different singer: needs a confirmed title and duration, never artist substrings.
            if (!exactTitle || delta == null) return null
        } else if (!exactTitle && !title.contains(otherTitle) && !otherTitle.contains(title)) {
            return null
        }
        val otherAlbum = candidateAlbum.orEmpty().lyricsMatchKey()
        val exactAlbum = album.isNotEmpty() && album == otherAlbum
        val conflictingAlbum = album.isNotEmpty() && otherAlbum.isNotEmpty() && !exactAlbum
        val candidateCover = lyricsCoverTagged(candidateTitle)
        val coverMismatch = coverTagged != candidateCover
        // A file labeled as a cover may use the original recording's lyrics; the reverse may not.
        val coverCompatible = coverTagged && !candidateCover && exactTitle && delta != null && delta <= 8_000L
        val score = (if (exactTitle) 60 else 28) + when {
            exactArtists -> 28
            artistOverlap -> 18
            else -> 6
        } + (if (exactAlbum) 12 else 0) + when {
            delta == null -> 0
            delta <= 3_000L -> 24
            delta <= 8_000L -> 12
            else -> -14
        }
        val automatic = when {
            coverCompatible -> true
            coverMismatch || crossArtist -> false
            else -> exactTitle && exactArtists && when {
                delta == null -> exactAlbum
                conflictingAlbum -> delta <= 3_000L
                else -> delta <= 8_000L
            }
        }
        val fast = automatic && delta != null && delta <= 3_000L && (!conflictingAlbum || coverCompatible)
        return Match(score, automatic, fast, exactArtists)
    }

    data class Match(
        val score: Int,
        val automatic: Boolean,
        val fast: Boolean,
        val sameArtists: Boolean,
    )
}
