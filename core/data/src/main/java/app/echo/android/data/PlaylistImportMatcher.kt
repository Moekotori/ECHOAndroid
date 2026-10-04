package app.echo.android.data

import app.echo.android.model.library.PlaylistImportLimits
import app.echo.android.model.library.PlaylistImportPreview
import java.util.Locale

/** Index only the requested entries; library projections can be streamed in bounded batches. */
internal class PlaylistImportMatcher(private val entries: List<M3uEntry>) {
    private data class Candidate(var id: String? = null, var ambiguous: Boolean = false) {
        fun add(value: String) { if (id == null) id = value else if (id != value) ambiguous = true }
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
        val indexed = hashMapOf<Pair<String, String?>, Int>()
        entries.forEachIndexed { index, entry ->
            val previous = indexed.putIfAbsent(entry.location to entry.title, index)
            if (previous != null) {
                candidates[index] = candidates[previous]
                corroborated[index] = corroborated[previous]
                return@forEachIndexed
            }
            uris.getOrPut(entry.location) { arrayListOf() }.add(index)
            val path = normalizeM3uLocation(entry.location).folded()
            paths.getOrPut(path) { arrayListOf() }.add(index)
            suffixes(path).forEach { suffixPaths.getOrPut(it) { arrayListOf() }.add(index) }
            files.getOrPut(path.substringAfterLast('/')) { arrayListOf() }.add(index)
            entry.title?.trim()?.takeIf(String::isNotBlank)?.let { display ->
                val separator = display.indexOf(" - ")
                if (separator > 0) {
                    metadata.getOrPut(display.substring(0, separator).folded() + "\u0000" + display.substring(separator + 3).folded()) { arrayListOf() }.add(index)
                } else titles.getOrPut(display.folded()) { arrayListOf() }.add(index)
            }
        }
    }

    fun add(rows: List<M3uMatchRow>) {
        rows.forEach { row ->
            uris[row.contentUri]?.forEach { candidates[it][0].add(row.id) }
            val path = row.fileLocation()?.let { normalizeM3uLocation(it).folded() }
            path?.let {
                suffixes(path).forEach { suffix -> paths[suffix]?.forEach { candidates[it][1].add(row.id) } }
                suffixPaths[path]?.forEach { candidates[it][1].add(row.id) }
                files[path.substringAfterLast('/')]?.forEach { candidates[it][2].add(row.id) }
            }
            fun metadataMatch(index: Int) {
                candidates[index][3].add(row.id)
                val location = locations[index]
                if (path != null && (path == location || path.endsWith("/$location") ||
                    location.endsWith("/$path") || path.substringAfterLast('/') == location.substringAfterLast('/'))) {
                    corroborated[index].add(row.id)
                }
            }
            metadata[row.artist.folded() + "\u0000" + row.title.folded()]?.forEach(::metadataMatch)
            titles[row.title.folded()]?.forEach(::metadataMatch)
        }
    }

    fun preview(): PlaylistImportPreview {
        val ids = linkedSetOf<String>()
        val unresolved = arrayListOf<String>()
        var duplicates = 0
        var unmatched = 0
        var ambiguous = 0
        candidates.forEachIndexed { index, levels ->
            val strongest = levels.firstOrNull { it.id != null }
            val candidate = if (strongest?.ambiguous == true && corroborated[index].id != null)
                corroborated[index] else strongest
            if (candidate == null || candidate.ambiguous) {
                if (candidate?.ambiguous == true) ambiguous++ else unmatched++
                if (unresolved.size < PlaylistImportLimits.MaxUnresolvedPreview) {
                    unresolved += entries[index].title ?: entries[index].location.substringAfterLast('/')
                }
            } else if (!ids.add(requireNotNull(candidate.id))) duplicates++
        }
        return PlaylistImportPreview(ids.toList(), entries.size, duplicates, unmatched, ambiguous, unresolved)
    }

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
