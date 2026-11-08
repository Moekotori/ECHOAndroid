package app.echo.android.model.library

data class PlaylistImportPreview(
    val trackIds: List<String>,
    val totalEntries: Int,
    val duplicateEntries: Int,
    val unmatchedEntries: Int,
    val ambiguousEntries: Int,
    val unresolvedNames: List<String>,
    val entries: List<PlaylistImportEntry> = emptyList(),
    val skippedEntries: Int = 0,
)

enum class PlaylistImportMatch { Matched, Missing, Ambiguous, Manual, Skipped }
data class PlaylistImportCandidate(val id: String, val title: String, val artist: String, val location: String)
data class PlaylistImportEntry(val index: Int, val name: String, val match: PlaylistImportMatch,
    val selected: PlaylistImportCandidate? = null, val candidates: List<PlaylistImportCandidate> = emptyList())

fun PlaylistImportPreview.corrected(index: Int, candidate: PlaylistImportCandidate?): PlaylistImportPreview {
    require(index in entries.indices)
    return playlistImportPreview(entries.map { row -> if (row.index != index) row else row.copy(
        selected = candidate, match = if (candidate == null) PlaylistImportMatch.Skipped else PlaylistImportMatch.Manual) })
}

fun playlistImportPreview(entries: List<PlaylistImportEntry>): PlaylistImportPreview {
    val selected = entries.mapNotNull { it.selected?.id }
    val ids = selected.distinct()
    val unresolved = entries.filter { it.match == PlaylistImportMatch.Missing || it.match == PlaylistImportMatch.Ambiguous }
    return PlaylistImportPreview(ids, entries.size, selected.size - ids.size,
        entries.count { it.match == PlaylistImportMatch.Missing }, entries.count { it.match == PlaylistImportMatch.Ambiguous },
        unresolved.take(PlaylistImportLimits.MaxUnresolvedPreview).map { it.name }, entries,
        entries.count { it.match == PlaylistImportMatch.Skipped })
}

object PlaylistImportLimits {
    const val MaxBytes = 256 * 1024
    const val MaxEntries = 2_000
    const val MaxUnresolvedPreview = 12
}
