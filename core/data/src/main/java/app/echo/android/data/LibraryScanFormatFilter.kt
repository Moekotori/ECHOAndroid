package app.echo.android.data

import app.echo.android.model.library.LibraryScanOptions

/** Import-only rule: existing songs remain eligible for refresh and deletion detection. */
internal fun LibraryScanOptions.acceptsFileFormat(fileName: String?, alreadyImported: Boolean): Boolean {
    if (alreadyImported || allowedExtensions.isEmpty()) return true
    val extension = fileName.orEmpty().substringAfterLast('.', missingDelimiterValue = "")
    return extension.isNotEmpty() && allowedExtensions.any { it.equals(extension, ignoreCase = true) }
}
