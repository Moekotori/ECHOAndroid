package app.echo.android.data

data class LibraryHygieneResult(
    val missingRemoved: Int = 0,
    val duplicatesRemoved: Int = 0,
) {
    val changed: Boolean get() = missingRemoved > 0 || duplicatesRemoved > 0
}

object LibraryHygienePolicy {
    const val MaxCheckBatch = 400

    data class DuplicateGroup(
        val fingerprint: String,
        val keepId: String,
        val removeIds: List<String>,
    )

    fun missingIds(
        tracks: List<Pair<String, String>>,
        exists: (uri: String) -> Boolean,
    ): List<String> =
        tracks.mapNotNull { (id, uri) ->
            id.takeIf { uri.isNotBlank() && !exists(uri) }
        }

    fun duplicateGroups(rows: List<Pair<String, String>>): List<DuplicateGroup> =
        rows
            .filter { it.second.isNotBlank() }
            .groupBy { it.second }
            .mapNotNull { (fingerprint, group) ->
                if (group.size < 2) return@mapNotNull null
                val sorted = group.map { it.first }.sortedWith(
                    compareBy<String> { id -> if (id.startsWith("mediastore:")) 0 else 1 }
                        .thenBy { it },
                )
                DuplicateGroup(
                    fingerprint = fingerprint,
                    keepId = sorted.first(),
                    removeIds = sorted.drop(1),
                )
            }

    fun idsToDelete(missing: List<String>, duplicates: List<DuplicateGroup>): List<String> =
        (missing + duplicates.flatMap { it.removeIds }).distinct()
}
