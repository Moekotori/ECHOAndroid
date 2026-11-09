package app.echo.android.data

import app.echo.android.model.library.*
import java.util.EnumMap

/** Per-scan, bounded samples; counts include deferred metadata reads as a separate category. */
internal class ScanSkipReport {
    private val counts = EnumMap<LibraryScanSkipReason, Int>(LibraryScanSkipReason::class.java)
    private val examples = ArrayList<LibraryScanSkippedItem>()
    fun record(item: LibraryScanSkippedItem) {
        counts[item.reason] = (counts[item.reason] ?: 0) + 1
        if (examples.size < 40) examples += item.copy(name = item.name.take(256), path = item.path?.take(1_024))
    }
    fun counts(): Map<LibraryScanSkipReason, Int> = counts.toMap()
    fun samples(): List<LibraryScanSkippedItem> = examples.toList()
}

internal fun LibraryScanOptions.includesFileName(name: String?): Boolean =
    name == null || excludedNameKeywords.none { it.isNotBlank() && name.contains(it, ignoreCase = true) }

internal fun LibraryScanOptions.rejectionReason(durationMs: Long, sizeBytes: Long,
    relativePath: String?, name: String?): LibraryScanSkipReason? = explicitRejection(relativePath, name) ?: when {
    durationMs > 0 && durationMs < minDurationMs -> LibraryScanSkipReason.Duration
    sizeBytes > 0 && sizeBytes < minSizeBytes -> LibraryScanSkipReason.Size
    else -> null
}
