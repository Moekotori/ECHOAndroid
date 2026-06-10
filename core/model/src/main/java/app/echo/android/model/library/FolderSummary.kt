package app.echo.android.model.library

data class FolderSummary(
    val folderKey: String,
    val path: String?,
    val artworkUri: String?,
    val trackCount: Int,
    val albumCount: Int,
    val artistCount: Int,
    val durationMs: Long,
    val totalSizeBytes: Long,
    val latestModifiedSeconds: Long,
)
