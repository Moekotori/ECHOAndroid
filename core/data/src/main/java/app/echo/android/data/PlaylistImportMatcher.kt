package app.echo.android.data

import app.echo.android.model.library.PlaylistImportLimits
import app.echo.android.model.library.PlaylistImportPreview
import app.echo.android.model.library.PlaylistImportCandidate
import app.echo.android.model.library.PlaylistImportEntry
import app.echo.android.model.library.PlaylistImportMatch
import app.echo.android.model.library.playlistImportPreview
import java.util.Locale

/** Index only the requested entries; library projections can be streamed in bounded batches. */
internal class PlaylistImportMatcher(private val entries: List<M3uEntry>) {
    private data class Candidate(var id: String? = null, var ambiguous: Boolean = false) {
        val choices = linkedMapOf<String, PlaylistImportCandidate>()
        fun add(row: M3uMatchRow) {
            val value = row.id
            if (id == null) id = value else if (id != value) ambiguous = true
            if (choices.size < 6 || value in choices) choices[value] = PlaylistImportCandidate(value, row.title, row.artist, row.fileLocation() ?: row.contentUri)
        }
    }
    private val candidates = Array(entries.size) { Array(4) { Candidate() } }
    private val corroborated = Array(entries.size) { Candidate() }
    private val locations = entries.map { normalizeM3uLocation(it.location).folded() }
    private val uris = HashMap<String, MutableList<Int>>()
    private val paths = HashMap<String, MutableList<Int>>()
    private val suffixPaths = HashMap<String, MutableList<Int>>()
    private val files = HashMap<String, MutableList<Int>>()
    private val metadata = HashMap<String, MutableList<Int>>()
    private val titles = HashMap<String, MutableList<Int>>()

    init {
        require(entries.size <= PlaylistImportLimits.MaxEntries)
        val indexed = hashMapOf<Triple<String, String?, String?>, Int>()
        entries.forEachIndexed { index, entry ->
            val previous = indexed.putIfAbsent(Triple(entry.location, entry.title, entry.artist), index)
            if (previous != null) {
                candidates[index] = candidates[previous]
                corroborated[index] = corroborated[previous]
                return@forEachIndexed
            }
            val path = locations[index]
            if (path.isNotBlank()) {
                uris.getOrPut(entry.location) { arrayListOf() }.add(index)
                paths.getOrPut(path) { arrayListOf() }.add(index)
                suffixes(path).forEach { suffixPaths.getOrPut(it) { arrayListOf() }.add(index) }
                files.getOrPut(path.substringAfterLast('/')) { arrayListOf() }.add(index)
            }
            entry.title?.trim()?.takeIf(String::isNotBlank)?.let { display ->
                val separator = if (entry.artist == null) display.indexOf(" - ") else -1
                val title = if (separator > 0) display.substring(separator + 3) else display
                val artist = entry.artist ?: if (separator > 0) display.substring(0, separator) else ""
                if (artist.isBlank()) titles.getOrPut(title.folded()) { arrayListOf() }.add(index)
                else metadata.getOrPut(artist.folded() + "\u0000" + title.folded()) { arrayListOf() }.add(index)
            }
        }
    }

    fun add(rows: List<M3uMatchRow>) {
        rows.forEach { row ->
            uris[row.contentUri]?.forEach { candidates[it][0].add(row) }
            val path = row.fileLocation()?.let { normalizeM3uLocation(it).folded() }
            path?.let {
                suffixes(path).forEach { suffix -> paths[suffix]?.forEach { candidates[it][1].add(row) } }
                suffixPaths[path]?.forEach { candidates[it][1].add(row) }
                files[path.substringAfterLast('/')]?.forEach { candidates[it][2].add(row) }
            }
            fun metadataMatch(index: Int) {
                candidates[index][3].add(row)
                val location = locations[index]
                if (location.isNotEmpty() && path != null && (path == location || path.endsWith("/$location") ||
                    location.endsWith("/$path") || path.substringAfterLast('/') == location.substringAfterLast('/'))) {
                    corroborated[index].add(row)
                }
            }
            metadata[row.artist.folded() + "\u0000" + row.title.folded()]?.forEach(::metadataMatch)
            titles[row.title.folded()]?.forEach(::metadataMatch)
        }
    }

    fun preview(): PlaylistImportPreview = playlistImportPreview(entries.mapIndexed { index, entry ->
        val strongest = candidates[index].firstOrNull { it.id != null }
        val candidate = if (strongest?.ambiguous == true && corroborated[index].id != null) corroborated[index] else strongest
        val match = when { candidate == null -> PlaylistImportMatch.Missing; candidate.ambiguous -> PlaylistImportMatch.Ambiguous; else -> PlaylistImportMatch.Matched }
        PlaylistImportEntry(index, entry.title ?: entry.location.substringAfterLast('/'), match,
            if (match == PlaylistImportMatch.Matched) candidate?.choices?.get(candidate.id) else null,
            candidate?.choices?.values?.toList().orEmpty())
    })

    private fun String.folded() = trim().lowercase(Locale.ROOT)
    private fun suffixes(path: String): Sequence<String> = sequence {
        yield(path)
        var offset = path.indexOf('/')
        var count = 1
        while (offset >= 0 && offset < path.lastIndex && count++ < 32) {
            yield(path.substring(offset + 1))
            offset = path.indexOf('/', offset + 1)
        }
    }
}
