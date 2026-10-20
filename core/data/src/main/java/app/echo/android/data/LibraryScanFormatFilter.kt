package app.echo.android.data

import app.echo.android.model.library.LibraryScanOptions

/** Import-only rule: existing songs remain eligible for refresh and deletion detection. */
internal fun LibraryScanOptions.acceptsFileFormat(fileName: String?, alreadyImported: Boolean): Boolean {
    if (alreadyImported || allowedExtensions.isEmpty()) return true
    val extension = fileName.orEmpty().substringAfterLast('.', missingDelimiterValue = "")
    return extension.isNotEmpty() && allowedExtensions.any {
        scanFormatFamily(it) == scanFormatFamily(extension)
    }
}

/** Alternate suffixes describe the same selectable format, including existing saved filters. */
private fun scanFormatFamily(extension: String): String = when (val lower = extension.lowercase(java.util.Locale.ROOT)) {
    "fla" -> "flac"
    "wave", "bwf" -> "wav"
    "adts" -> "aac"
    "ec3" -> "eac3"
    else -> lower
}
