package app.echo.android.data

import app.echo.android.model.library.AlbumSummary
import java.text.Normalizer
import java.util.Locale

/** Android query policy aligned with ECHOSteam's album lookup, with bounded fallbacks. */
internal object AlbumOnlineQueryPolicy {
    private val collaboration = Regex("""\s*(?:/|&|＆|\+|×)\s*|\s+(?:and|x|with|ft\.?|feat(?:uring)?\.?)\s+""", RegexOption.IGNORE_CASE)

    fun artist(album: AlbumSummary): String? = listOf(album.albumArtist, album.artist)
        .firstOrNull { !it.isNullOrBlank() && it.trim().lowercase(Locale.ROOT) !in setOf("unknown", "unknown artist", "未知艺术家", "未知藝人") }
        ?.trim()

    fun artistParts(value: String): List<String> {
        val name = Normalizer.normalize(value, Normalizer.Form.NFKC).trim()
        val parts = name.split(collaboration).map(String::trim).filter(String::isNotEmpty).distinct()
        return if (parts.size in 2..4 && parts.all { it.count(Char::isLetterOrDigit) >= 2 } &&
            parts.any { it.count(Char::isLetterOrDigit) >= 4 }) parts else listOf(name)
    }

    fun musicBrainzQueries(album: AlbumSummary): List<String> {
        val artist = artist(album) ?: return emptyList()
        val parts = artistParts(artist)
        val artists = (listOf(parts.joinToString(" & ")) + parts + artist).distinct()
        // Try both exact and cleaned titles before widening a collaborator query.
        return artists.flatMap { name -> AlbumOnlineInfoParser.titleVariants(album.title).map { title ->
            AlbumOnlineInfoParser.query(album.copy(title = title, albumArtist = name))
        } }.distinct().take(4)
    }

    fun wikipediaLanguages(preferred: String, title: String, artist: String): List<String> {
        val japanese = Regex("[\\u3040-\\u30ff\\uff66-\\uff9f]").containsMatchIn("$title $artist")
        return (if (japanese) listOf("ja", preferred, "en", "zh") else listOf(preferred, "en", "ja", "zh")).distinct()
    }

    fun wikipediaTitles(title: String): List<String> {
        val trimmed = title.trim().replace('（', '(').replace('）', ')')
        if (!trimmed.endsWith(')')) return AlbumOnlineInfoParser.titleVariants(title)
        var depth = 0
        for (index in trimmed.indices.reversed()) {
            when (trimmed[index]) { ')' -> depth++; '(' -> depth-- }
            if (depth == 0) {
                val suffix = trimmed.substring(index)
                // Search-only cleanup for performer/event annotations, including nested parentheses.
                // Never collapse a live/remix work into its studio counterpart.
                if (Regex("""\b(?:live|remix|acoustic|instrumental|soundtrack|score)\b|现场|現場|ライブ""", RegexOption.IGNORE_CASE).containsMatchIn(suffix)) break
                val base = trimmed.substring(0, index).trim()
                if (base.isNotEmpty()) return (listOf(base) + AlbumOnlineInfoParser.titleVariants(title)).distinct().take(2)
                break
            }
        }
        return AlbumOnlineInfoParser.titleVariants(title)
    }
}
