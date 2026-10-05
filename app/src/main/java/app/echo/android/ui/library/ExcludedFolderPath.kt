package app.echo.android.ui.library

import app.echo.android.data.LibraryScanPolicy

/** Match the same primary/removable path representation used by both local scanners. */
internal fun excludedFolderPath(authority: String?, documentId: String?): String? {
    if (authority != "com.android.externalstorage.documents" || documentId == null || ':' !in documentId) return null
    val (volume, rawPath) = LibraryScanPolicy.splitDocumentTreeId(documentId) ?: return null
    if (volume == "." || volume == ".." || volume.any { it == '/' || it == '\\' }) return null
    val parts = rawPath.split('/').filter(String::isNotBlank)
    if (parts.isEmpty() || parts.any { it == "." || it == ".." }) return null
    return LibraryScanPolicy.documentTreeRelativePath(volume, parts.joinToString("/"))?.trim('/')
}
