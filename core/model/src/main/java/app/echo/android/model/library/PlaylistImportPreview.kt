package app.echo.android.model.library

data class PlaylistImportPreview(
    val trackIds: List<String>,
    val totalEntries: Int,
    val duplicateEntries: Int,
    val unmatchedEntries: Int,
    val ambiguousEntries: Int,
    val unresolvedNames: List<String>,
)

object PlaylistImportLimits {
    const val MaxBytes = 256 * 1024
    const val MaxEntries = 2_000
    const val MaxUnresolvedPreview = 12
}
