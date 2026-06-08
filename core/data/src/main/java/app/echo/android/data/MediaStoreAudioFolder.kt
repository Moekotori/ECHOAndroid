package app.echo.android.data

import android.net.Uri
import android.provider.DocumentsContract

data class MediaStoreAudioFolder(
    val displayName: String,
    val relativePathPrefix: String,
) {
    companion object {
        fun fromTreeUri(uri: Uri): MediaStoreAudioFolder? {
            val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
                ?: return null
            val parts = documentId.split(":", limit = 2)
            val volume = parts.firstOrNull()?.lowercase().orEmpty()
            if (volume != "primary") return null

            val path = parts.getOrNull(1)
                ?.replace('\\', '/')
                ?.trim('/')
                ?.takeIf { it.isNotBlank() }
                ?: return null

            val relativePath = normalizeRelativePathPrefix(path) ?: return null
            return MediaStoreAudioFolder(
                displayName = path.substringAfterLast('/'),
                relativePathPrefix = relativePath,
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
