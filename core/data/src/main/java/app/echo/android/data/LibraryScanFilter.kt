package app.echo.android.data

import app.echo.android.model.library.CueSheetPolicy
import app.echo.android.model.library.LibraryScanOptions
import app.echo.android.model.library.LibraryScanSkipReason

internal fun LibraryScanOptions.accepts(durationMs: Long, sizeBytes: Long, relativePath: String?): Boolean {
    // Some supported formats/providers cannot report duration or size. Unknown is not zero-length audio.
    if (durationMs > 0L && durationMs < minDurationMs.coerceAtLeast(0L)) return false
    if (sizeBytes > 0L && sizeBytes < minSizeBytes.coerceAtLeast(0L)) return false
    return includesDirectory(relativePath)
}

/** Compute once per snapshot: restoring a CUE's whole file is a refresh, not a new import. */
internal fun importedLocalScanIds(ids: Set<String>): Set<String> = buildSet(ids.size) {
    addAll(ids)
    ids.forEach { add(CueSheetPolicy.baseTrackId(it)) }
}

internal fun LibraryScanOptions.includesDirectory(relativePath: String?): Boolean {
    val pathKey = relativePath.orEmpty().scanPathKey()
    if (ignoresFolderPath(pathKey)) return false
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
    onRejected: (LibraryTrackEntity, app.echo.android.model.library.LibraryScanSkipReason) -> Unit = { _, _ -> },
): List<LibraryTrackEntity> = batch.filter {
    val rejection = options.explicitRejection(it.relativePath, it.fileName)
    if (rejection != null) {
        onRejected(it, rejection)
        return@filter false
    }
    if (it.id in existingIds) return@filter true
    // Cue titles inherit the image file's import; short movements must not hit min duration.
    if (CueSheetPolicy.isCueTrackId(it.id)) return@filter true
    options.accepts(it.durationMs, it.sizeBytes, null).also { accepted ->
        if (!accepted) onRejected(it, options.rejectionReason(it.durationMs, it.sizeBytes, null, it.fileName)
            ?: app.echo.android.model.library.LibraryScanSkipReason.Duration)
    }
}

internal fun LibraryScanOptions.explicitRejection(relativePath: String?, name: String?): LibraryScanSkipReason? = when {
    !includesDirectory(relativePath) -> LibraryScanSkipReason.Directory
    !includesFileName(name) -> LibraryScanSkipReason.Name
    name != null && ignoredPathsByFolder.isNotEmpty() &&
        ignoresFolderPath("${relativePath.orEmpty().trimEnd('/')}/$name".scanPathKey()) -> LibraryScanSkipReason.Name
    blocksFileFormat(name) -> LibraryScanSkipReason.Format
    else -> null
}

private val NonMusicFolderNames = setOf(
    "ringtones", "notifications", "alarms", "recordings", "recorder", "voicerecorder",
    "voice recorder", "callrecordings", "call recordings", "voicenotes", "voice notes",
    "voice_recorder", "voice_notes", "sound_recorder", "soundrecorder", "sound recorder",
    "call_rec", "call_recording", "call_recordings", "callrecording", "call recorder",
    "sound recordings", "whatsapp audio", "whatsapp voice notes", "telegram audio",
    "wechat", "weixin", "micromsg", "录音", "通话录音", "铃声", "通知音",
    "录音机", "錄音", "錄音機", "通話錄音", "鈴聲",
)
