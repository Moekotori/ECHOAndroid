package app.echo.android.data

import app.echo.android.model.library.LibraryScanOptions

/** The allowlist is import-only. An explicit block applies to every matching file. */
internal fun LibraryScanOptions.acceptsFileFormat(fileName: String?, alreadyImported: Boolean): Boolean {
    if (blocksFileFormat(fileName)) return false
    if (alreadyImported || allowedExtensions.isEmpty()) return true
    val extension = fileName.orEmpty().substringAfterLast('.', missingDelimiterValue = "")
    val family = scanFormatFamily(extension)
    return family.isNotEmpty() && allowedExtensions.any { scanFormatFamily(it) == family }
}

internal fun LibraryScanOptions.blocksFileFormat(fileName: String?): Boolean =
    blockedExtensions.isNotEmpty() &&
        scanFormatFamily(fileName.orEmpty().substringAfterLast('.', "")) in blockedExtensions

/** Alternate suffixes describe the same selectable format, including existing saved filters. */
internal fun scanFormatFamily(extension: String): String = when (val lower = extension.lowercase(java.util.Locale.ROOT)) {
    "fla" -> "flac"
    "wave", "bwf" -> "wav"
    "adts" -> "aac"
    "ec3" -> "eac3"
    else -> lower
}
