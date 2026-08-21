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
            val parts = documentId.split(":", limit = 2)
            val rawVolume = parts.firstOrNull().orEmpty()
            val volume = rawVolume.lowercase()

            val path = parts.getOrNull(1)
                ?.replace('\\', '/')
                ?.trim('/')
                .orEmpty()

            if (volume != "primary") {
                val displayName = path.substringAfterLast('/').takeIf { it.isNotBlank() } ?: rawVolume
                val safeVolume = rawVolume.replace(':', '_').ifBlank { "removable" }
                val relativePath = normalizeRelativePathPrefix(
                    listOf("Removable", safeVolume, path)
                        .filter { it.isNotBlank() }
                        .joinToString("/"),
                ) ?: return null
                return MediaStoreAudioFolder(
                    displayName = displayName,
                    relativePathPrefix = relativePath,
                    treeUri = uri,
                )
            }

            val relativePath = normalizeRelativePathPrefix(path) ?: return null
            return MediaStoreAudioFolder(
                displayName = path.substringAfterLast('/'),
                relativePathPrefix = relativePath,
                treeUri = if (LibraryScanPolicy.usesDocumentTreeScan(volume)) uri else null,
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
