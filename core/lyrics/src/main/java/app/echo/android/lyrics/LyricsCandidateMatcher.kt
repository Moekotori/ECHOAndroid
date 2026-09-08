package app.echo.android.lyrics

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** Prepare the target once per search; provider scores share the same scale. */
internal class LyricsCandidateMatcher(private val request: EchoLyricsSearchRequest) {
    private val title = request.title.matchKey()
    private val artists = artistKeys(request.artist)
    private val album = request.album.orEmpty().matchKey()
    private val versions = versionKeys(request.title)
    private val languages = lyricsLanguageVersions(request.title, request.album)

    fun match(candidateTitle: String, candidateArtist: String, candidateAlbum: String?, durationMs: Long): Match? {
        val otherLanguages = lyricsLanguageVersions(candidateTitle, candidateAlbum)
        // Explicit singing-language conflicts override matching names, albums and duration.
        if (languages.isNotEmpty() && otherLanguages.isNotEmpty() && languages != otherLanguages) return null
        val otherTitle = candidateTitle.matchKey()
        val otherArtists = artistKeys(candidateArtist)
        if (title.isBlank() || otherTitle.isBlank() || artists.isEmpty() || otherArtists.isEmpty()) return null
        val exactTitle = title == otherTitle
        val exactArtists = artists == otherArtists
        // Never use substrings of artist names as identity evidence (Ann != Joanne).
        if (!exactArtists && artists.intersect(otherArtists).isEmpty()) return null
        if (!exactTitle && !title.contains(otherTitle) && !otherTitle.contains(title)) return null
        if (versions != versionKeys(candidateTitle)) return null
        val delta = if (request.durationMs > 0 && durationMs > 0) abs(request.durationMs - durationMs) else null
        if (delta != null && delta > 15_000L) return null
        val otherAlbum = candidateAlbum.orEmpty().matchKey()
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

private val NonLetters = Regex("""[^\p{L}\p{N}]+""")
// Keep spaces within an artist name; split only explicit collaboration separators.
private val ArtistSeparators = Regex("""\s*(?:[/;；、&＆]|\b(?:feat|ft|featuring)\.?(?![\p{L}\p{N}]))\s*""", RegexOption.IGNORE_CASE)
private val VersionPatterns = listOf(
    "live", "remix", "instrumental", "karaoke", "acoustic", "demo", "unplugged",
    "radio[\\s-]*edit", "tv[\\s-]*(?:size|edit|version)", "short[\\s-]*(?:ver(?:sion)?|edit)",
    "extended", "sped[\\s-]*up", "slowed", "remaster(?:ed)?", "mono", "stereo",
).map { Regex("(?<![\\p{L}\\p{N}])(?:$it)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE) }
private val CjkVersions = listOf("现场", "現場", "伴奏", "不插电", "不插電", "纯音乐", "純音樂", "重制", "重製")

private fun String.matchKey(): String = Normalizer.normalize(this, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).replace(NonLetters, "")

private fun artistKeys(value: String): Set<String> = ArtistSeparators
    .split(Normalizer.normalize(value, Normalizer.Form.NFKC))
    .map { it.matchKey() }.filter { it.isNotBlank() }.toSet()

private fun versionKeys(value: String): Set<Int> {
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
    return buildSet {
        VersionPatterns.forEachIndexed { index, regex -> if (regex.containsMatchIn(normalized)) add(index) }
        CjkVersions.forEachIndexed { index, text -> if (normalized.contains(text)) add(VersionPatterns.size + index) }
    }
}
