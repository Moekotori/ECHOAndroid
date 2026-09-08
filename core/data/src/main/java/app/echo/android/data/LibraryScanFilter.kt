package app.echo.android.data

import app.echo.android.model.library.LibraryScanOptions

internal fun LibraryScanOptions.accepts(durationMs: Long, sizeBytes: Long, relativePath: String?): Boolean {
    // Some supported formats/providers cannot report duration or size. Unknown is not zero-length audio.
    if (durationMs > 0L && durationMs < minDurationMs.coerceAtLeast(0L)) return false
    if (sizeBytes > 0L && sizeBytes < minSizeBytes.coerceAtLeast(0L)) return false
    return includesDirectory(relativePath)
}

internal fun LibraryScanOptions.includesDirectory(relativePath: String?): Boolean {
    val folders = relativePath.orEmpty().replace('\\', '/').split('/')
    if (excludeHiddenFolders && folders.any { it.startsWith('.') }) return false
    if (excludeNonMusicFolders && folders.any { it.lowercase(java.util.Locale.ROOT) in NonMusicFolderNames }) return false
    return true
}

/** Existing songs remain eligible for metadata refresh, regardless of new import filters. */
internal fun filterLocalScanBatch(
    batch: List<LibraryTrackEntity>,
    existingIds: Set<String>,
    options: LibraryScanOptions,
): List<LibraryTrackEntity> = batch.filter {
    it.id in existingIds || options.accepts(it.durationMs, it.sizeBytes, it.relativePath)
}

private val NonMusicFolderNames = setOf(
    "ringtones", "notifications", "alarms", "recordings", "recorder", "voicerecorder",
    "voice recorder", "callrecordings", "call recordings", "录音", "通话录音", "铃声", "通知音",
)
