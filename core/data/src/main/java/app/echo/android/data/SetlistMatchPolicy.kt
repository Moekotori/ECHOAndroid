package app.echo.android.data

import app.echo.android.model.library.ArtistSetlist
import app.echo.android.model.library.ArtistSetlistSong
import java.text.Normalizer

data class SetlistMatchRow(
    val id: String,
    val title: String,
    val artist: String,
    val normalizedTitle: String,
    val normalizedArtist: String,
)

object SetlistMatchPolicy {
    fun matchTitle(title: String): String = title.normalizedSetlistTitle()

    fun match(
        setlist: ArtistSetlist,
        rows: List<SetlistMatchRow>,
    ): ArtistSetlist {
        if (rows.isEmpty()) return setlist
        val byTitle = rows.groupBy { it.normalizedTitle }
        val artistHint = setlist.artistName.normalizedSetlistTitle()
        return setlist.copy(
            songs = setlist.songs.map { song ->
                if (song.tape || song.title.isBlank()) return@map song
                val key = song.title.normalizedSetlistTitle()
                if (key.isEmpty()) return@map song
                val candidates = byTitle[key].orEmpty()
                val chosen = when {
                    candidates.size == 1 -> candidates.first()
                    candidates.size > 1 -> {
                        val narrowed = candidates.filter { row ->
                            artistHint.isNotEmpty() && row.normalizedArtist.contains(artistHint) ||
                                song.artist?.normalizedSetlistTitle()?.let { hint ->
                                    hint.isNotEmpty() && row.normalizedArtist.contains(hint)
                                } == true
                        }
                        narrowed.singleOrNull()
                    }
                    else -> null
                }
                song.copy(trackId = chosen?.id)
            },
        )
    }

    fun playableTrackIds(songs: List<ArtistSetlistSong>): List<String> =
        songs.mapNotNull { it.trackId }.distinct()
}

internal fun String.normalizedSetlistTitle(): String {
    val nfkc = Normalizer.normalize(this, Normalizer.Form.NFKC).lowercase().trim()
    return nfkc
        .replace(SetlistNoise, " ")
        .replace(Whitespace, " ")
        .trim()
}

private val SetlistNoise = Regex(
    """\((?:live|live version|acoustic|remix|edit|radio edit)\)|-\s*live\b|feat\.?.+|ft\.?.+|featuring.+$""",
    RegexOption.IGNORE_CASE,
)
private val Whitespace = Regex("\\s+")
