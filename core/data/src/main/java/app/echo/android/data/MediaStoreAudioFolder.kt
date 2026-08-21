package app.echo.android.data

import android.net.Uri
import android.provider.DocumentsContract

data class MediaStoreAudioFolder(
    val displayName: String,
    val relativePathPrefix: String,
    val treeUri: Uri? = null,
) {
    companion object {
        fun fromTreeUri(uri: Uri): MediaStoreAudioFolder? {
            val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
                ?: return null
            val (rawVolume, path) = LibraryScanPolicy.splitDocumentTreeId(documentId) ?: return null
            val relativePath = LibraryScanPolicy.documentTreeRelativePath(rawVolume, path) ?: return null
            val displayName = path.substringAfterLast('/').takeIf { it.isNotBlank() } ?: rawVolume
            return MediaStoreAudioFolder(
                displayName = displayName,
                relativePathPrefix = relativePath,
                treeUri = if (LibraryScanPolicy.usesDocumentTreeScan(rawVolume)) uri else null,
            )
        }
    }
}

internal fun normalizeRelativePathPrefix(path: String?): String? {
    val cleanPath = path
        ?.replace('\\', '/')
        ?.trim('/')
        ?.takeIf { it.isNotBlank() }
        ?: return null
    return "$cleanPath/"
}

internal fun escapeSqlLikeArgument(value: String): String =
    buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '\\', '%', '_' -> append('\\')
            }
            append(char)
        }
    }
