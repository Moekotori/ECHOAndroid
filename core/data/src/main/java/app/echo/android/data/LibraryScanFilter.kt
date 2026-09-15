package app.echo.android.data

import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.LibraryScanOptions

internal fun LibraryScanOptions.accepts(durationMs: Long, sizeBytes: Long, relativePath: String?): Boolean {
    // Some supported formats/providers cannot report duration or size. Unknown is not zero-length audio.
    if (durationMs > 0L && durationMs < minDurationMs.coerceAtLeast(0L)) return false
    if (sizeBytes > 0L && sizeBytes < minSizeBytes.coerceAtLeast(0L)) return false
    return includesDirectory(relativePath)
}

internal fun LibraryScanOptions.includesDirectory(relativePath: String?): Boolean {
    val path = relativePath.orEmpty().replace('\\', '/').trim('/')
    val pathKey = path.lowercase(java.util.Locale.ROOT)
    if (excludedRelativePaths.any { excluded ->
        val excludedKey = excluded.lowercase(java.util.Locale.ROOT)
        pathKey == excludedKey || pathKey.startsWith("$excludedKey/")
    }) {
        return false
    }
    val folders = relativePath.orEmpty().replace('\\', '/').split('/')
    if (excludeHiddenFolders && folders.any { it.startsWith('.') }) return false
    if (excludeNonMusicFolders && folders.any { it.lowercase(java.util.Locale.ROOT) in NonMusicFolderNames }) return false
    return true
}

/**
 * Directory exclusion drops imported rows so the cleanup pass can delete them.
 * Duration, size and format still refresh existing songs.
 */
internal fun filterLocalScanBatch(
    batch: List<LibraryTrackEntity>,
    existingIds: Set<String>,
    options: LibraryScanOptions,
): List<LibraryTrackEntity> = batch.filter {
    if (!options.includesDirectory(it.relativePath)) return@filter false
    if (it.id in existingIds) return@filter true
    // Cue titles inherit the image file's import; short movements must not hit min duration.
    if (CueSheetPolicy.isCueTrackId(it.id)) return@filter true
    options.accepts(it.durationMs, it.sizeBytes, null)
}

private val NonMusicFolderNames = setOf(
    "ringtones", "notifications", "alarms", "recordings", "recorder", "voicerecorder",
    "voice recorder", "callrecordings", "call recordings", "voicenotes", "voice notes",
    "sound recordings", "whatsapp audio", "whatsapp voice notes", "telegram audio",
    "wechat", "weixin", "micromsg", "录音", "通话录音", "铃声", "通知音",
)
